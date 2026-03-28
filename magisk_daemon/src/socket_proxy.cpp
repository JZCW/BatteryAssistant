#include "socket_proxy.h"
#include "logger.h"
#include <cstring>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <fcntl.h>
#include <errno.h>
#include <arpa/inet.h>
#include <fstream>

SocketProxy::SocketProxy(const std::string& appSocketName,
                         const std::string& daemonSocketName,
                         const std::string& daemonBinaryPath)
    : appSocketName(appSocketName), daemonSocketName(daemonSocketName),
      daemonBinaryPath(daemonBinaryPath),
      appSocketFd(-1), daemonSocketFd(-1),
      running(false), daemonConnecting(false), daemonUnavailable(false),
      daemonStartAttempts(0), isFirstConnectAttempt(true),
      lastAppActivityTime(time(nullptr)) {
    wakeupPipe[0] = -1;
    wakeupPipe[1] = -1;
}

SocketProxy::~SocketProxy() {
    stop();
}

// connectToDaemon: 创建新连接，成功时将新fd写入daemonSocketFd（加锁）
bool SocketProxy::connectToDaemon() {
    int newFd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (newFd < 0) {
        LOG_ERROR("Failed to create daemon socket: " + std::string(strerror(errno)));
        return false;
    }

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    addr.sun_path[0] = '\0';  // Abstract namespace
    strncpy(&addr.sun_path[1], daemonSocketName.c_str(), sizeof(addr.sun_path) - 2);
    socklen_t addrLen = offsetof(struct sockaddr_un, sun_path) + 1 + daemonSocketName.length();

    if (connect(newFd, (struct sockaddr*)&addr, addrLen) < 0) {
        LOG_ERROR("Failed to connect to daemon: " + std::string(strerror(errno)));
        close(newFd);
        return false;
    }

    // 设置非阻塞模式
    int flags = fcntl(newFd, F_GETFL, 0);
    fcntl(newFd, F_SETFL, flags | O_NONBLOCK);

    std::lock_guard<std::mutex> lock(daemonFdMutex);
    // 关闭旧fd（以防万一）
    if (daemonSocketFd >= 0) {
        close(daemonSocketFd);
    }
    daemonSocketFd = newFd;
    LOG_INFO("Connected to daemon socket: " + daemonSocketName);
    return true;
}

bool SocketProxy::connectToApp() {
    appSocketFd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (appSocketFd < 0) {
        LOG_ERROR("Failed to create app socket: " + std::string(strerror(errno)));
        return false;
    }

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    addr.sun_path[0] = '\0';  // Abstract namespace
    strncpy(&addr.sun_path[1], appSocketName.c_str(), sizeof(addr.sun_path) - 2);
    socklen_t addrLen = offsetof(struct sockaddr_un, sun_path) + 1 + appSocketName.length();

    if (connect(appSocketFd, (struct sockaddr*)&addr, addrLen) < 0) {
        LOG_ERROR("Failed to connect to app: " + std::string(strerror(errno)));
        close(appSocketFd);
        appSocketFd = -1;
        return false;
    }

    // 设置非阻塞模式
    int flags = fcntl(appSocketFd, F_GETFL, 0);
    fcntl(appSocketFd, F_SETFL, flags | O_NONBLOCK);

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
            if (errno == EINTR) continue;
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                fd_set readFds;
                FD_ZERO(&readFds);
                FD_SET(fd, &readFds);
                struct timeval tv = {10, 0}; // 10秒超时
                int ret = select(fd + 1, &readFds, nullptr, nullptr, &tv);
                if (ret <= 0) {
                    if (ret == 0) LOG_WARN("Timeout reading message length");
                    else LOG_ERROR("Select error while reading message length: " + std::string(strerror(errno)));
                    return false;
                }
                continue;
            }
            LOG_ERROR("Failed to read message length: " + std::string(strerror(errno)));
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
            if (errno == EINTR) continue;
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                fd_set readFds;
                FD_ZERO(&readFds);
                FD_SET(fd, &readFds);
                struct timeval tv = {10, 0}; // 10秒超时
                int ret = select(fd + 1, &readFds, nullptr, nullptr, &tv);
                if (ret <= 0) {
                    if (ret == 0) LOG_WARN("Timeout reading message data");
                    else LOG_ERROR("Select error while reading message data: " + std::string(strerror(errno)));
                    return false;
                }
                continue;
            }
            LOG_ERROR("Failed to read message data: " + std::string(strerror(errno)));
            return false;
        }
        if (n == 0) {
            LOG_DEBUG("Connection closed while reading message data");
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
            if (errno == EINTR) continue;
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
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
        if (n <= 0) {
            LOG_ERROR("Connection closed or error while writing message length");
            return false;
        }
        totalWritten += n;
    }

    // 写入消息数据
    totalWritten = 0;
    while (totalWritten < buffer.size()) {
        ssize_t n = write(fd, buffer.data() + totalWritten, buffer.size() - totalWritten);
        if (n < 0) {
            if (errno == EINTR) continue;
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
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
        if (n <= 0) {
            LOG_ERROR("Connection closed or error while writing message data");
            return false;
        }
        totalWritten += n;
    }

    return true;
}

// 检查daemon abstract socket是否已绑定（通过 /proc/net/unix 查找条目）
bool SocketProxy::isDaemonSocketBound() {
    std::ifstream unixFile("/proc/net/unix");
    if (!unixFile.is_open()) {
        // 无法读取时乐观处理，让connect自己失败
        return true;
    }
    std::string line;
    std::string target = "@" + daemonSocketName;
    while (std::getline(unixFile, line)) {
        if (line.find(target) != std::string::npos) {
            return true;
        }
    }
    return false;
}

// 向App发送JSON错误响应（与daemon正常响应格式兼容）
bool SocketProxy::sendErrorToApp(const std::string& errorCode) {
    std::string json = "{\"success\":false,\"error\":\"" + errorCode + "\"}";
    std::vector<char> buf(json.begin(), json.end());
    return writeMessage(appSocketFd, buf);
}

// 启动daemon进程（双 fork 避免僵尸）
static void spawnDaemon(const std::string& binaryPath) {
    pid_t pid = fork();
    if (pid < 0) {
        return;
    }
    if (pid == 0) {
        // 第一层子进程：再 fork 一次，让孙进程被 init 收养
        pid_t grandchild = fork();
        if (grandchild == 0) {
            setsid();
            // 关闭不需要的fd
            for (int fd = 3; fd < 256; fd++) close(fd);
            execl(binaryPath.c_str(), binaryPath.c_str(), nullptr);
            _exit(1);
        }
        _exit(0); // 第一层子进程立即退出
    }
    // 父进程回收第一层子进程（不会阻塞）
    waitpid(pid, nullptr, 0);
}

// 后台重连线程
void SocketProxy::reconnectLoop() {
    auto finishWith = [this](char cmd) {
        daemonConnecting.store(false);
        write(wakeupPipe[1], &cmd, 1);
    };

    if (!isFirstConnectAttempt) {
        // 检查二进制文件是否存在
        if (!daemonBinaryPath.empty() && access(daemonBinaryPath.c_str(), F_OK) != 0) {
            LOG_ERROR("[Reconnect] Daemon binary not found: " + daemonBinaryPath
                      + ", giving up permanently");
            daemonUnavailable.store(true);
            return finishWith('f');
        }

        // 如果 socket 未绑定，尝试启动 daemon
        if (!isDaemonSocketBound()) {
            daemonStartAttempts++;
            if (daemonStartAttempts > 3) {
                LOG_ERROR("[Reconnect] Daemon failed to start after 3 attempts, giving up");
                daemonUnavailable.store(true);
                return finishWith('f');
            }
            LOG_WARN("[Reconnect] Daemon not running, starting it (attempt "
                     + std::to_string(daemonStartAttempts) + "/3)");
            spawnDaemon(daemonBinaryPath);

            // 最多等 2 秒让 daemon 完成启动
            for (int i = 0; i < 4; i++) {
                usleep(500000);
                if (isDaemonSocketBound()) break;
            }
            if (!isDaemonSocketBound()) {
                LOG_WARN("[Reconnect] Daemon still not ready after start attempt "
                         + std::to_string(daemonStartAttempts));
                return finishWith('f');
            }
        }
    }
    isFirstConnectAttempt = false;

    if (connectToDaemon()) {
        daemonStartAttempts = 0; // 连接成功，重置计数
        LOG_INFO("[Reconnect] Daemon connected successfully");
        return finishWith('c');
    } else {
        LOG_WARN("[Reconnect] Daemon connect failed");
        return finishWith('f');
    }
}

// 安全启动后台重连（防止重复创建线程）
void SocketProxy::startBackgroundReconnect() {
    if (daemonUnavailable.load()) {
        LOG_DEBUG("[Reconnect] Daemon permanently unavailable, skipping");
        return;
    }
    bool expected = false;
    if (!daemonConnecting.compare_exchange_strong(expected, true)) {
        // 已有重连线程在运行
        return;
    }
    // 等待上一个reconnectThread（若有）退出
    if (reconnectThread.joinable()) {
        reconnectThread.join();
    }
    reconnectThread = std::thread(&SocketProxy::reconnectLoop, this);
}

void SocketProxy::runProxy() {
    LOG_INFO("Proxy thread started");

    while (running) {
        // 每次循环获取daemon fd的快照（加锁）
        int localDaemonFd;
        {
            std::lock_guard<std::mutex> lock(daemonFdMutex);
            localDaemonFd = daemonSocketFd;
        }

        fd_set readFds;
        FD_ZERO(&readFds);
        FD_SET(appSocketFd, &readFds);

        int maxFd = appSocketFd;

        // 监听wakeupPipe读端（重连通知 / stop通知）
        if (wakeupPipe[0] >= 0) {
            FD_SET(wakeupPipe[0], &readFds);
            if (wakeupPipe[0] > maxFd) maxFd = wakeupPipe[0];
        }

        // 只有在Daemon连接正常时才监听daemon socket
        if (localDaemonFd >= 0) {
            FD_SET(localDaemonFd, &readFds);
            if (localDaemonFd > maxFd) maxFd = localDaemonFd;
        }

        // 等待事件
        int ready = select(maxFd + 1, &readFds, nullptr, nullptr, nullptr);

        if (ready < 0) {
            if (errno == EINTR) continue;
            LOG_ERROR("Select error: " + std::string(strerror(errno)));
            break;
        }

        // 处理wakeupPipe事件（重连完成 或 stop信号）
        if (wakeupPipe[0] >= 0 && FD_ISSET(wakeupPipe[0], &readFds)) {
            char cmds[16];
            ssize_t nr = read(wakeupPipe[0], cmds, sizeof(cmds));
            for (ssize_t i = 0; i < nr; i++) {
                if (cmds[i] == 'c') {
                    std::lock_guard<std::mutex> lock(daemonFdMutex);
                    LOG_INFO("Daemon reconnected, fd=" + std::to_string(daemonSocketFd));
                } else if (cmds[i] == 's') {
                    running = false;
                }
            }
            if (!running) break;
        }

        // 处理App消息
        if (FD_ISSET(appSocketFd, &readFds)) {
            std::vector<char> buffer;

            if (!readMessage(appSocketFd, buffer)) {
                LOG_INFO("App disconnected or error reading message, proxy will exit");
                running = false;
                break;
            }

            lastAppActivityTime.store(time(nullptr));

            // 获取最新daemon fd
            {
                std::lock_guard<std::mutex> lock(daemonFdMutex);
                localDaemonFd = daemonSocketFd;
            }

            // Daemon未连接：立即返回错误，后台重连
            if (localDaemonFd < 0) {
                LOG_WARN("Daemon not connected, returning error to app and starting background reconnect");
                sendErrorToApp("daemon_unavailable");
                startBackgroundReconnect();
                continue;
            }

            // 转发到Daemon
            if (!writeMessage(localDaemonFd, buffer)) {
                LOG_ERROR("Failed to write message to daemon, closing connection");
                {
                    std::lock_guard<std::mutex> lock(daemonFdMutex);
                    close(daemonSocketFd);
                    daemonSocketFd = -1;
                }
                // 立即给app返回错误
                sendErrorToApp("daemon_write_error");
                startBackgroundReconnect();
                continue;
            }
        }

        // 处理Daemon消息
        {
            std::lock_guard<std::mutex> lock(daemonFdMutex);
            localDaemonFd = daemonSocketFd;
        }
        if (localDaemonFd >= 0 && FD_ISSET(localDaemonFd, &readFds)) {
            std::vector<char> buffer;

            if (!readMessage(localDaemonFd, buffer)) {
                LOG_WARN("Daemon disconnected or read error, will reconnect on next request");
                {
                    std::lock_guard<std::mutex> lock(daemonFdMutex);
                    close(daemonSocketFd);
                    daemonSocketFd = -1;
                }
                continue;
            }

            // 转发到App
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

    // 创建自唤醒管道
    if (pipe(wakeupPipe) < 0) {
        LOG_ERROR("Failed to create wakeup pipe: " + std::string(strerror(errno)));
        return false;
    }
    // 写端设为非阻塞，防止管道满时阻塞
    int flags = fcntl(wakeupPipe[1], F_GETFL, 0);
    fcntl(wakeupPipe[1], F_SETFL, flags | O_NONBLOCK);

    // 连接到App - 必须成功
    if (!connectToApp()) {
        LOG_ERROR("Failed to connect to app, proxy cannot start");
        close(wakeupPipe[0]); wakeupPipe[0] = -1;
        close(wakeupPipe[1]); wakeupPipe[1] = -1;
        return false;
    }

    running = true;

    // 首次连接Daemon在后台线程中异步进行（不阻塞proxy启动）
    isFirstConnectAttempt = true;
    startBackgroundReconnect();

    proxyThread = std::thread(&SocketProxy::runProxy, this);

    LOG_INFO("Socket proxy started successfully");
    return true;
}

void SocketProxy::stop() {
    LOG_INFO("Stopping socket proxy...");
    running = false;

    // 通过wakeupPipe发送stop信号，让select优雅退出
    if (wakeupPipe[1] >= 0) {
        char cmd = 's';
        write(wakeupPipe[1], &cmd, 1);
    }

    // 关闭app socket（兜底，确保select返回）
    if (appSocketFd >= 0) {
        close(appSocketFd);
        appSocketFd = -1;
    }

    // 等待proxy线程退出
    if (proxyThread.joinable()) {
        proxyThread.join();
    }

    // 等待后台重连线程退出
    if (reconnectThread.joinable()) {
        reconnectThread.join();
    }

    // 关闭daemon socket
    {
        std::lock_guard<std::mutex> lock(daemonFdMutex);
        if (daemonSocketFd >= 0) {
            close(daemonSocketFd);
            daemonSocketFd = -1;
        }
    }

    // 关闭wakeupPipe
    if (wakeupPipe[0] >= 0) { close(wakeupPipe[0]); wakeupPipe[0] = -1; }
    if (wakeupPipe[1] >= 0) { close(wakeupPipe[1]); wakeupPipe[1] = -1; }

    LOG_INFO("Socket proxy stopped");
}
