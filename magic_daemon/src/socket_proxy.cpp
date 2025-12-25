#include "socket_proxy.h"
#include "logger.h"
#include <cstring>
#include <unistd.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>

SocketProxy::SocketProxy(const std::string& appSocketName, const std::string& daemonSocketName)
    : appSocketName(appSocketName), daemonSocketName(daemonSocketName),
      appSocketFd(-1), daemonSocketFd(-1), running(false) {
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
        FD_SET(daemonSocketFd, &readFds);
        
        int maxFd = (appSocketFd > daemonSocketFd) ? appSocketFd : daemonSocketFd;
        
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
            char buffer[8192];
            ssize_t bytesRead = read(appSocketFd, buffer, sizeof(buffer));
            
            if (bytesRead <= 0) {
                LOG_INFO("App disconnected");
                running = false;
                break;
            }
            
            // 转发到守护进程
            ssize_t bytesWritten = write(daemonSocketFd, buffer, bytesRead);
            if (bytesWritten != bytesRead) {
                LOG_ERROR("Failed to write to daemon: " + std::string(strerror(errno)));
                break;
            }
        }
        
        if (FD_ISSET(daemonSocketFd, &readFds)) {
            char buffer[8192];
            ssize_t bytesRead = read(daemonSocketFd, buffer, sizeof(buffer));
            
            if (bytesRead <= 0) {
                LOG_INFO("Daemon disconnected");
                running = false;
                break;
            }
            
            // 转发到应用
            ssize_t bytesWritten = write(appSocketFd, buffer, bytesRead);
            if (bytesWritten != bytesRead) {
                LOG_ERROR("Failed to write to app: " + std::string(strerror(errno)));
                break;
            }
        }
    }
    
    LOG_INFO("Proxy thread exiting");
}

bool SocketProxy::start() {
    LOG_INFO("Starting socket proxy...");
    
    // 连接到应用
    if (!connectToApp()) {
        return false;
    }
    
    // 连接到守护进程
    if (!connectToDaemon()) {
        close(appSocketFd);
        appSocketFd = -1;
        return false;
    }
    
    running = true;
    proxyThread = std::thread(&SocketProxy::runProxy, this);
    
    LOG_INFO("Socket proxy started successfully");
    return true;
}

void SocketProxy::stop() {
    LOG_INFO("Stopping socket proxy...");
    running = false;
    
    if (proxyThread.joinable()) {
        proxyThread.join();
    }
    
    if (appSocketFd >= 0) {
        close(appSocketFd);
        appSocketFd = -1;
    }
    
    if (daemonSocketFd >= 0) {
        close(daemonSocketFd);
        daemonSocketFd = -1;
    }
    
    LOG_INFO("Socket proxy stopped");
}
