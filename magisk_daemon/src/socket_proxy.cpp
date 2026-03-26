#include "socket_proxy.h"
#include "logger.h"
#include <cstring>
#include <unistd.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>
#include <arpa/inet.h>

SocketProxy::SocketProxy(const std::string& appSocketName, const std::string& daemonSocketName)
    : appSocketName(appSocketName), daemonSocketName(daemonSocketName),
      appSocketFd(-1), daemonSocketFd(-1), running(false), lastAppActivityTime(time(nullptr)) {
}

SocketProxy::~SocketProxy() {
    stop();
}

bool SocketProxy::connectToDaemon() {
    // 创建socket连接到守护进程的abstract namespace socket
    daemonSocketFd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (daemonSocketFd < 0) {
        LOG_ERROR("Failed to create daemon socket: " + std::string(strerror(errno)));
        return false;
    }
    
    // 设置abstract namespace socket地址
    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    addr.sun_path[0] = '\0';  // Abstract namespace
    strncpy(&addr.sun_path[1], daemonSocketName.c_str(), sizeof(addr.sun_path) - 2);
    
    // 计算地址长度
    socklen_t addrLen = offsetof(struct sockaddr_un, sun_path) + 1 + daemonSocketName.length();
    
    // 连接到守护进程
    if (connect(daemonSocketFd, (struct sockaddr*)&addr, addrLen) < 0) {
        LOG_ERROR("Failed to connect to daemon: " + std::string(strerror(errno)));
        close(daemonSocketFd);
        daemonSocketFd = -1;
        return false;
    }
    
    LOG_INFO("Connected to daemon socket: " + daemonSocketName);
    return true;
}

bool SocketProxy::connectToApp() {
    // 创建socket连接到应用的abstract namespace socket
    appSocketFd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (appSocketFd < 0) {
        LOG_ERROR("Failed to create app socket: " + std::string(strerror(errno)));
        return false;
    }
    
    // 设置abstract namespace socket地址
    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    addr.sun_path[0] = '\0';  // Abstract namespace
    strncpy(&addr.sun_path[1], appSocketName.c_str(), sizeof(addr.sun_path) - 2);
    
    // 计算地址长度
    socklen_t addrLen = offsetof(struct sockaddr_un, sun_path) + 1 + appSocketName.length();
    
    // 连接到应用
    if (connect(appSocketFd, (struct sockaddr*)&addr, addrLen) < 0) {
        LOG_ERROR("Failed to connect to app: " + std::string(strerror(errno)));
        close(appSocketFd);
        appSocketFd = -1;
        return false;
    }
    
    LOG_INFO("Connected to app socket: " + appSocketName);
    return true;
}

// 读取完整消息（4字节长度 + 数据）
bool SocketProxy::readMessage(int fd, std::vector<char>& buffer) {
    // 读取消息长度（网络字节序）
    int32_t length;
    size_t totalRead = 0;
    while (totalRead < sizeof(length)) {
        ssize_t n = read(fd, reinterpret_cast<char*>(&length) + totalRead, sizeof(length) - totalRead);
        if (n < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                // 等待socket可读
                fd_set readFds;
                FD_ZERO(&readFds);
                FD_SET(fd, &readFds);
                if (select(fd + 1, &readFds, nullptr, nullptr, nullptr) < 0) {
                    LOG_ERROR("Select error while reading message length: " + std::string(strerror(errno)));
                    return false;
                }
                continue;
            }
            if (n == 0) {
                LOG_DEBUG("Connection closed while reading message length");
            } else {
                LOG_ERROR("Failed to read message length: " + std::string(strerror(errno)));
            }
            return false;
        }
        if (n == 0) {
            LOG_DEBUG("Connection closed while reading message length");
            return false;
        }
        totalRead += n;
    }
    
    // 转换为主机字节序
    length = ntohl(length);
    
    // 验证长度
    if (length <= 0 || length > 1024 * 1024) {
        LOG_ERROR("Invalid message length: " + std::to_string(length));
        return false;
    }
    
    // 读取消息数据
    buffer.resize(length);
    totalRead = 0;
    while (totalRead < static_cast<size_t>(length)) {
        ssize_t n = read(fd, buffer.data() + totalRead, length - totalRead);
        if (n < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                // 等待socket可读
                fd_set readFds;
                FD_ZERO(&readFds);
                FD_SET(fd, &readFds);
                if (select(fd + 1, &readFds, nullptr, nullptr, nullptr) < 0) {
                    LOG_ERROR("Select error while reading message data: " + std::string(strerror(errno)));
                    return false;
                }
                continue;
            }
            LOG_ERROR("Failed to read message data: " + std::string(strerror(errno)));
            return false;
        }
        if (n == 0) {
            LOG_ERROR("Connection closed while reading message data");
            return false;
        }
        totalRead += n;
    }
    
    return true;
}

// 写入完整消息（4字节长度 + 数据）
bool SocketProxy::writeMessage(int fd, const std::vector<char>& buffer) {
    int32_t length = static_cast<int32_t>(buffer.size());
    int32_t networkLength = htonl(length);
    
    // 写入消息长度
    size_t totalWritten = 0;
    while (totalWritten < sizeof(networkLength)) {
        ssize_t n = write(fd, reinterpret_cast<char*>(&networkLength) + totalWritten, sizeof(networkLength) - totalWritten);
        if (n < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                // 等待socket可写
                fd_set writeFds;
                FD_ZERO(&writeFds);
                FD_SET(fd, &writeFds);
                if (select(fd + 1, nullptr, &writeFds, nullptr, nullptr) < 0) {
                    LOG_ERROR("Select error while writing message length: " + std::string(strerror(errno)));
                    return false;
                }
                continue;
            }
            LOG_ERROR("Failed to write message length: " + std::string(strerror(errno)));
            return false;
        }
        if (n == 0) {
            LOG_ERROR("Connection closed while writing message length");
            return false;
        }
        totalWritten += n;
    }
    
    // 写入消息数据
    totalWritten = 0;
    while (totalWritten < buffer.size()) {
        ssize_t n = write(fd, buffer.data() + totalWritten, buffer.size() - totalWritten);
        if (n < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                // 等待socket可写
                fd_set writeFds;
                FD_ZERO(&writeFds);
                FD_SET(fd, &writeFds);
                if (select(fd + 1, nullptr, &writeFds, nullptr, nullptr) < 0) {
                    LOG_ERROR("Select error while writing message data: " + std::string(strerror(errno)));
                    return false;
                }
                continue;
            }
            LOG_ERROR("Failed to write message data: " + std::string(strerror(errno)));
            return false;
        }
        if (n == 0) {
            LOG_ERROR("Connection closed while writing message data");
            return false;
        }
        totalWritten += n;
    }
    
    return true;
}

void SocketProxy::runProxy() {
    LOG_INFO("Proxy thread started");
    
    // 设置非阻塞模式
    int flags = fcntl(appSocketFd, F_GETFL, 0);
    fcntl(appSocketFd, F_SETFL, flags | O_NONBLOCK);
    
    flags = fcntl(daemonSocketFd, F_GETFL, 0);
    fcntl(daemonSocketFd, F_SETFL, flags | O_NONBLOCK);
    
    while (running) {
        fd_set readFds;
        FD_ZERO(&readFds);
        FD_SET(appSocketFd, &readFds);
        
        int maxFd = appSocketFd;
        
        // 只有在Daemon连接正常时才监听daemon socket
        if (daemonSocketFd >= 0) {
            FD_SET(daemonSocketFd, &readFds);
            if (daemonSocketFd > maxFd) {
                maxFd = daemonSocketFd;
            }
        }
        
        // 等待数据可读
        int ready = select(maxFd + 1, &readFds, nullptr, nullptr, nullptr);
        
        if (ready < 0) {
            if (errno == EINTR) {
                continue;
            }
            LOG_ERROR("Select error: " + std::string(strerror(errno)));
            break;
        }
        
        // 检查哪个socket有数据
        if (FD_ISSET(appSocketFd, &readFds)) {
            std::vector<char> buffer;
            
            if (!readMessage(appSocketFd, buffer)) {
                LOG_INFO("App disconnected or error reading message, proxy will exit");
                running = false;
                break;
            }
            
            lastAppActivityTime.store(time(nullptr));
            
            // 如果Daemon未连接，尝试连接
            if (daemonSocketFd < 0) {
                LOG_WARN("Daemon not connected, attempting to reconnect...");
                if (!connectToDaemon()) {
                    LOG_ERROR("Failed to reconnect to daemon, discarding app data");
                    continue;
                }
                // 重新设置非阻塞模式
                flags = fcntl(daemonSocketFd, F_GETFL, 0);
                fcntl(daemonSocketFd, F_SETFL, flags | O_NONBLOCK);
            }
            
            // 转发到守护进程
            if (!writeMessage(daemonSocketFd, buffer)) {
                LOG_ERROR("Failed to write message to daemon");
                close(daemonSocketFd);
                daemonSocketFd = -1;
            }
        }
        
        if (daemonSocketFd >= 0 && FD_ISSET(daemonSocketFd, &readFds)) {
            std::vector<char> buffer;
            
            if (!readMessage(daemonSocketFd, buffer)) {
                LOG_WARN("Daemon disconnected or error reading message, will attempt to reconnect on next request");
                close(daemonSocketFd);
                daemonSocketFd = -1;
                continue;
            }
            
            // 转发到应用
            if (!writeMessage(appSocketFd, buffer)) {
                LOG_ERROR("Failed to write message to app");
                running = false;
                break;
            }
        }
    }
    
    LOG_INFO("Proxy thread exiting");
}

bool SocketProxy::start() {
    LOG_INFO("Starting socket proxy...");
    
    // 连接到应用 - 必须成功
    if (!connectToApp()) {
        LOG_ERROR("Failed to connect to app, proxy cannot start");
        return false;
    }
    
    // 尝试连接到守护进程，但即使失败也继续运行（稍后会重连）
    if (!connectToDaemon()) {
        LOG_WARN("Failed to connect to daemon initially, will retry on first request");
    }
    
    running = true;
    proxyThread = std::thread(&SocketProxy::runProxy, this);
    
    LOG_INFO("Socket proxy started successfully");
    return true;
}

void SocketProxy::stop() {
    LOG_INFO("Stopping socket proxy...");
    running = false;
    
    // 关闭app socket，这会导致select返回并退出循环
    if (appSocketFd >= 0) {
        close(appSocketFd);
        appSocketFd = -1;
    }
    
    // 关闭daemon socket
    if (daemonSocketFd >= 0) {
        close(daemonSocketFd);
        daemonSocketFd = -1;
    }
    
    // 等待线程退出
    if (proxyThread.joinable()) {
        proxyThread.join();
    }
    
    LOG_INFO("Socket proxy stopped");
}
