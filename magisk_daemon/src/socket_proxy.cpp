#include "socket_proxy.h"
#include "logger.h"
#include "socket_utils.h"
#include <json/json.h>
#include <cstring>
#include <unistd.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <sys/resource.h>
#include <fcntl.h>
#include <errno.h>
#include <arpa/inet.h>
#include <sys/syscall.h>
#include <linux/close_range.h>
#include <climits>

namespace {
constexpr int MAX_DAEMON_START_ATTEMPTS = 3;
constexpr int DAEMON_START_WAIT_RETRIES = 4;
constexpr useconds_t DAEMON_START_WAIT_US = 500000;
constexpr int IO_TIMEOUT_SEC = 10;

// Non-blocking wakeup pipe write with explicit error handling.
// Returns true only when one byte is successfully queued.
bool sendWakeupCommand(int fd, char cmd) {
    if (fd < 0) {
        return false;
    }

    for (;;) {
        ssize_t n = write(fd, &cmd, 1);
        if (n == 1) {
            return true;
        }
        if (n < 0 && errno == EINTR) {
            continue;
        }
        return false;
    }
}

// 创建 abstract namespace socket 并连接
// 成功返回阻塞 fd（带 SO_RCVTIMEO/SO_SNDTIMEO）；失败记录日志并返回 -1
static int connectAbstractSocket(const std::string& name, const std::string& label) {
    int newFd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (newFd < 0) {
        LOG_ERROR("Failed to create " + label + " socket: " + std::string(strerror(errno)));
        return -1;
    }
    struct sockaddr_un addr;
    socklen_t addrLen = 0;
    if (!buildAbstractSockaddr(name, addr, addrLen)) {
        // 失败原因统一按“名称超长”记录，便于定位配置错误。
        const size_t maxAbstractNameLen = sizeof(addr.sun_path) - 1;
        LOG_ERROR("Abstract socket name too long: " + std::to_string(name.size()) +
                  " bytes (max " + std::to_string(maxAbstractNameLen) + ")");
        close(newFd);
        return -1;
    }
    if (connect(newFd, (struct sockaddr*)&addr, addrLen) < 0) {
        LOG_ERROR("Failed to connect to " + label + ": " + std::string(strerror(errno)));
        close(newFd);
        return -1;
    }
    // 读写超时由内核 socket 选项处理，避免 read/write 内部再套一层 select。
    struct timeval tv { IO_TIMEOUT_SEC, 0 };
    if (setsockopt(newFd, SOL_SOCKET, SO_RCVTIMEO, &tv, sizeof(tv)) < 0 ||
        setsockopt(newFd, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv)) < 0) {
        LOG_ERROR("Failed to set socket IO timeout on " + label + ": " + std::string(strerror(errno)));
        close(newFd);
        return -1;
    }
    return newFd;
}

// 主动 connect 探测 daemon 可达性，避免依赖 /proc/net/unix 的文本格式。
bool probeAbstractSocketReachable(const std::string& name) {
    int fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (fd < 0) {
        return false;
    }

    struct sockaddr_un addr;
    socklen_t addrLen = 0;
    if (!buildAbstractSockaddr(name, addr, addrLen)) {
        close(fd);
        return false;
    }

    bool reachable = (connect(fd, (struct sockaddr*)&addr, addrLen) == 0);
    close(fd);
    return reachable;
}
}

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
    int newFd = connectAbstractSocket(daemonSocketName, "daemon");
    if (newFd < 0) return false;

    std::lock_guard<std::mutex> lock(daemonFdMutex);
    if (daemonSocketFd >= 0) close(daemonSocketFd);
    daemonSocketFd = newFd;
    LOG_INFO("Connected to daemon socket: " + daemonSocketName);
    return true;
}

bool SocketProxy::connectToApp() {
    int newFd = connectAbstractSocket(appSocketName, "app");
    if (newFd < 0) return false;

    std::lock_guard<std::mutex> lock(appFdMutex);
    if (appSocketFd >= 0) close(appSocketFd);
    appSocketFd = newFd;
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
                LOG_WARN("Timeout reading message length");
                return false;
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
                LOG_WARN("Timeout reading message data");
                return false;
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
                LOG_WARN("Timeout writing message length");
                return false;
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
            if (errno == EINTR) continue;
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                LOG_WARN("Timeout writing message data");
                return false;
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

bool SocketProxy::isDaemonSocketReachable() {
    return probeAbstractSocketReachable(daemonSocketName);
}

// 向App发送JSON错误响应（与daemon正常响应格式兼容）
bool SocketProxy::sendErrorToApp(const std::string& errorCode) {
    // 使用 JsonCpp 序列化，避免手动拼接导致的 JSON 注入
    Json::Value response;
    response["success"] = false;
    response["error"] = errorCode;
    Json::StreamWriterBuilder builder;
    std::string json = Json::writeString(builder, response);
    std::vector<char> buf(json.begin(), json.end());
    int localAppFd = -1;
    {
        std::lock_guard<std::mutex> lock(appFdMutex);
        localAppFd = appSocketFd;
    }
    if (localAppFd < 0) {
        LOG_WARN("Cannot send error to app: app socket is unavailable");
        return false;
    }
    return writeMessage(localAppFd, buf);
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
            // 重定向 fd 0/1/2 到 /dev/null：守护进程脱离终端后继续持有父进程继承的
            // stdin/stdout/stderr 会导致意外的管道污染或 read 阻塞，使用 /dev/null 隔离。
            int devNull = open("/dev/null", O_RDWR);
            if (devNull >= 0) {
                dup2(devNull, STDIN_FILENO);
                dup2(devNull, STDOUT_FILENO);
                dup2(devNull, STDERR_FILENO);
                if (devNull > 2) close(devNull);
            }
            // 内核 >=5.9 优先使用 close_range，一次性关闭 3+ fd。
            int closeRangeRc = static_cast<int>(syscall(SYS_close_range, 3u, UINT_MAX, 0u));
            if (closeRangeRc != 0) {
                // 某些构建环境可能缺少 close_range 支持，回退到逐个关闭。
                LOG_WARN("close_range syscall failed, falling back to manual fd close: " + std::string(strerror(errno)));
                struct rlimit rl;
                int maxFd = (getrlimit(RLIMIT_NOFILE, &rl) == 0 && rl.rlim_cur != RLIM_INFINITY)
                            ? static_cast<int>(rl.rlim_cur) : 1024;
                for (int fd = 3; fd < maxFd; fd++) close(fd);
            }
            execl(binaryPath.c_str(), binaryPath.c_str(), nullptr);
            // execl 只在失败时返回；fd 3+ 已关闭，stderr 已重定向，无法写日志，直接退出
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
        // Reconnect notification is best-effort; failures are logged for diagnosis.
        if (!sendWakeupCommand(wakeupPipe[1], cmd)) {
            LOG_WARN("[Reconnect] Failed to notify proxy thread via wakeup pipe");
        }
    };

    if (!isFirstConnectAttempt) {
        // 检查二进制文件是否存在
        if (!daemonBinaryPath.empty() && access(daemonBinaryPath.c_str(), F_OK) != 0) {
            LOG_ERROR("[Reconnect] Daemon binary not found: " + daemonBinaryPath
                      + ", giving up permanently");
            daemonUnavailable.store(true);
            return finishWith('f');
        }

        // 如果 daemon 当前不可达，尝试启动 daemon。
        if (!isDaemonSocketReachable()) {
            daemonStartAttempts++;
            if (daemonStartAttempts > MAX_DAEMON_START_ATTEMPTS) {
                LOG_ERROR("[Reconnect] Daemon failed to start after " +
                          std::to_string(MAX_DAEMON_START_ATTEMPTS) +
                          " attempts, giving up");
                daemonUnavailable.store(true);
                return finishWith('f');
            }
            LOG_WARN("[Reconnect] Daemon not running, starting it (attempt "
                     + std::to_string(daemonStartAttempts) + "/"
                     + std::to_string(MAX_DAEMON_START_ATTEMPTS) + ")");
            spawnDaemon(daemonBinaryPath);

            // 固定等待窗口，给 daemon 留出 bind abstract socket 的时间。
            for (int i = 0; i < DAEMON_START_WAIT_RETRIES; i++) {
                usleep(DAEMON_START_WAIT_US);
                if (isDaemonSocketReachable()) break;
            }
            if (!isDaemonSocketReachable()) {
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

        int localAppFd;
        {
            std::lock_guard<std::mutex> lock(appFdMutex);
            localAppFd = appSocketFd;
        }
        if (localAppFd < 0) {
            LOG_WARN("App socket unavailable, proxy will exit");
            running = false;
            break;
        }

        fd_set readFds;
        FD_ZERO(&readFds);
        FD_SET(localAppFd, &readFds);

        int maxFd = localAppFd;

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

        // 固定本轮 select 的 daemon fd 快照，避免返回后被重连线程覆盖。
        const int selectedDaemonFd = localDaemonFd;

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
        if (FD_ISSET(localAppFd, &readFds)) {
            std::vector<char> buffer;

            if (!readMessage(localAppFd, buffer)) {
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

        // 处理Daemon消息：必须使用 select 前快照判断 FD_ISSET。
        if (selectedDaemonFd >= 0 && FD_ISSET(selectedDaemonFd, &readFds)) {
            std::vector<char> buffer;

            if (!readMessage(selectedDaemonFd, buffer)) {
                LOG_WARN("Daemon disconnected or read error, will reconnect on next request");
                {
                    std::lock_guard<std::mutex> lock(daemonFdMutex);
                    // 仅关闭当前登记的 fd，避免误关重连线程刚建立的新连接。
                    if (daemonSocketFd == selectedDaemonFd) {
                        close(daemonSocketFd);
                        daemonSocketFd = -1;
                    }
                }
                continue;
            }

            // 转发到App
            {
                std::lock_guard<std::mutex> lock(appFdMutex);
                localAppFd = appSocketFd;
            }
            if (localAppFd < 0 || !writeMessage(localAppFd, buffer)) {
                LOG_ERROR("Failed to write message to app");
                running = false;
                break;
            }
        }
    }

    // app fd 仅由proxy线程关闭，避免stop线程并发关闭造成竞态
    {
        std::lock_guard<std::mutex> lock(appFdMutex);
        if (appSocketFd >= 0) {
            close(appSocketFd);
            appSocketFd = -1;
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
    // 写端设为非阻塞，防止管道满时 stop() 的 write 阻塞导致停止流程挂起
    int flags = fcntl(wakeupPipe[1], F_GETFL, 0);
    if (flags < 0 || fcntl(wakeupPipe[1], F_SETFL, flags | O_NONBLOCK) < 0) {
        LOG_ERROR("Failed to set wakeupPipe write end non-blocking: " + std::string(strerror(errno)));
        close(wakeupPipe[0]); wakeupPipe[0] = -1;
        close(wakeupPipe[1]); wakeupPipe[1] = -1;
        return false;
    }

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
        if (!sendWakeupCommand(wakeupPipe[1], cmd)) {
            LOG_WARN("Failed to send stop signal via wakeup pipe, falling back to closing writer");
            // Closing writer forces EOF on read end, which also wakes select().
            close(wakeupPipe[1]);
            wakeupPipe[1] = -1;
        }
    }

    // app socket由proxy线程统一关闭，stop线程只负责唤醒并等待退出

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
