#include "socket_proxy.h"
#include "logger.h"
#include "config.h"
#include <iostream>
#include <csignal>
#include <unistd.h>
#include <cstdlib>
#include <cstdio>
#include <cstring>
#include <sstream>
#include <chrono>
#include <thread>
#include <sys/file.h>
#include <sys/wait.h>
#include <sys/select.h>
#include <fcntl.h>

static const char* PID_FILE      = Config::BRIDGE_PID_PATH;
static const char* DAEMON_BINARY = Config::DAEMON_BINARY_PATH;
static int g_pidFileFd = -1;
static int g_signalPipe[2] = {-1, -1};

volatile sig_atomic_t g_running = 1;

void signalHandler(int signal) {
    (void)signal;
    g_running = 0;
    // self-pipe 唤醒主循环，避免 sleep/SA_RESTART 导致的退出延迟。
    if (g_signalPipe[1] >= 0) {
        const char sig = 'x';
        ssize_t rc = write(g_signalPipe[1], &sig, 1);
        (void)rc;
    }
}

// 守护模式配置
static const int WATCHDOG_INTERVAL_SEC = 2903;    // 48'23" 一个质数
static const int WATCHDOG_MAX_FAILURES = 3;       // 连续失败次数上限
static const char* APP_PACKAGE = Config::APP_PACKAGE;
static const char* APP_SERVICE = Config::APP_SERVICE;

// 尝试获取 PID 文件锁，防止多实例
// retried：陈旧锁清除后的第二次尝试，防止无限递归
static bool acquirePidLock(bool retried = false) {
    g_pidFileFd = open(PID_FILE, O_CREAT | O_RDWR, 0644);
    if (g_pidFileFd < 0) {
        std::cerr << "Failed to open PID file: " << strerror(errno) << std::endl;
        return false;
    }

    // 尝试非阻塞独占锁
    if (flock(g_pidFileFd, LOCK_EX | LOCK_NB) < 0) {
        if (errno == EWOULDBLOCK) {
            // 重置文件指针至起始，避免 flock 后指针偏移导致读到空内容
            lseek(g_pidFileFd, 0, SEEK_SET);
            char buf[32] = {0};
            ssize_t readBytes = read(g_pidFileFd, buf, sizeof(buf) - 1);
            if (readBytes < 0) {
                // 读取 PID 失败时保守处理为“已有实例运行”，避免误用 kill(0, 0)。
                std::cerr << "Failed to read existing PID from lock file: " << strerror(errno) << std::endl;
                LOG_WARN("Failed to read existing PID from lock file, assuming another instance is active");
                close(g_pidFileFd);
                g_pidFileFd = -1;
                return false;
            }
            // 用 kill(pid, 0) 校验持锁进程是否真实存活，防止 PID 复用导致假阳性冲突退出
            pid_t existingPid = static_cast<pid_t>(atoi(buf));
            if (!retried && existingPid > 0 && kill(existingPid, 0) != 0 && errno == ESRCH) {
                // 持锁进程已死亡：清除陈旧锁后重试一次，无需人工删除 PID 文件
                std::cerr << "Stale PID lock (pid=" << buf << " is dead), clearing and retrying" << std::endl;
                LOG_WARN("Stale PID lock detected (pid=" + std::string(buf) + " no longer alive), clearing and retrying");
                close(g_pidFileFd);
                g_pidFileFd = -1;
                unlink(PID_FILE);
                return acquirePidLock(true); // retried=true 防止无限递归
            } else {
                std::cerr << "Another bridge instance is running (pid=" << buf << "), exiting" << std::endl;
                LOG_WARN("Another bridge instance is running (pid=" + std::string(buf) + "), exiting");
            }
        } else {
            std::cerr << "Failed to lock PID file: " << strerror(errno) << std::endl;
        }
        close(g_pidFileFd);
        g_pidFileFd = -1;
        return false;
    }

    // 写入当前 PID
    ftruncate(g_pidFileFd, 0);
    lseek(g_pidFileFd, 0, SEEK_SET);
    std::string pidStr = std::to_string(getpid());
    // 检查写入结果，避免磁盘满或权限问题导致 PID 文件为空
    if (write(g_pidFileFd, pidStr.c_str(), pidStr.size()) < 0) {
        std::cerr << "Failed to write PID file: " << strerror(errno) << std::endl;
        LOG_ERROR("Failed to write PID file, aborting startup to avoid ambiguous lock ownership");
        close(g_pidFileFd);
        g_pidFileFd = -1;
        return false;
    }
    return true;
}

static void releasePidLock() {
    if (g_pidFileFd >= 0) {
        flock(g_pidFileFd, LOCK_UN);
        close(g_pidFileFd);
        g_pidFileFd = -1;
        unlink(PID_FILE);
    }
}

// RAII 守卫：析构时自动调用 releasePidLock()，
// 确保正常返回、早返回、catch 分支等任意路径都能释放 PID 锁。
struct PidLockGuard {
    ~PidLockGuard() { releasePidLock(); }
};

void printUsage(const char* programName) {
    // Both parameters are abstract namespace names (not filesystem paths).
    std::cout << "Usage: " << programName << " <app_socket_name> <daemon_socket_name>" << std::endl;
    std::cout << "  app_socket_name:   Abstract namespace socket name for app connection" << std::endl;
    std::cout << "  daemon_socket_name: Abstract namespace socket name for daemon connection" << std::endl;
    std::cout << std::endl;
    std::cout << "Example: " << programName << " battery_service_app battery_service" << std::endl;
}

// 执行 shell 命令并返回输出
static std::string execCommand(const std::string& cmd) {
    std::string result;
    FILE* pipe = popen(cmd.c_str(), "r");
    if (!pipe) return result;
    char buf[256];
    while (fgets(buf, sizeof(buf), pipe)) {
        result += buf;
    }
    pclose(pipe);
    return result;
}

// 检查 app 是否处于 force-stopped 状态
static bool isAppForceStopped() {
    std::string cmd = "dumpsys package ";
    cmd += APP_PACKAGE;
    // 精确匹配 stopped=true，避免 last_stopped=true 等字段造成误判。
    cmd += " 2>/dev/null | grep -i '^\\s*stopped=true\\b'";
    std::string output = execCommand(cmd);
    return !output.empty();
}

// 检查 app 进程是否存在
static bool isAppProcessAlive() {
    std::string cmd = "pidof ";
    cmd += APP_PACKAGE;
    cmd += " 2>/dev/null";
    std::string output = execCommand(cmd);
    // 去掉尾部换行
    while (!output.empty() && (output.back() == '\n' || output.back() == '\r')) {
        output.pop_back();
    }
    return !output.empty();
}

// 通过 am startservice 唤醒 app 服务
static bool wakeUpApp() {
    std::string cmd = "am startservice -n ";
    cmd += APP_SERVICE;
    cmd += " --es start_source bridge_watchdog 2>&1";

    // 使用 popen/pclose 直接获取退出码，为主要判断依据
    FILE* pipe = popen(cmd.c_str(), "r");
    if (!pipe) {
        LOG_ERROR("[Watchdog] popen failed: " + std::string(strerror(errno)));
        return false;
    }
    std::string output;
    char buf[256];
    while (fgets(buf, sizeof(buf), pipe)) {
        output += buf;
    }
    int ret = pclose(pipe);
    LOG_INFO("[Watchdog] am startservice result (exit=" + std::to_string(ret) + "): " + output);

    // 以 shell 退出码为主要判断依据
    if (WIFEXITED(ret) && WEXITSTATUS(ret) == 0) {
        return true;
    }
    return false;
}

// Watchdog 检查（在主循环中被周期性调用）
// 返回 false 表示应退出
static bool watchdogCheck(int& consecutiveFailures, time_t lastAppActivity) {
    // 检查 app 是否被用户强行停止
    if (isAppForceStopped()) {
        LOG_INFO("[Watchdog] App is force-stopped by user, bridge should exit");
        return false;
    }

    // 检查距离最后一次 app 通信是否超过阈值
    time_t now = time(nullptr);
    long idleSec = static_cast<long>(now - lastAppActivity);
    if (idleSec < WATCHDOG_INTERVAL_SEC) {
        LOG_INFO("[Watchdog] App communicated " + std::to_string(idleSec) + "s ago, skip wake-up");
        consecutiveFailures = 0;
        return true;
    }

    bool processAlive = isAppProcessAlive();
    LOG_INFO("[Watchdog] App idle for " + std::to_string(idleSec) + "s, processAlive="
        + std::string(processAlive ? "true" : "false") + ", attempting wake-up");

    if (processAlive) {
        // 进程仍在时跳过 startservice，避免重复拉起造成额外噪音。
        LOG_INFO("[Watchdog] App process is alive, skip wake-up attempt");
        consecutiveFailures = 0;
        return true;
    }

    // 尝试唤醒 app
    if (wakeUpApp()) {
        LOG_INFO("[Watchdog] Wake-up succeeded");
        consecutiveFailures = 0;
    } else {
        consecutiveFailures++;
        LOG_WARN("[Watchdog] Wake-up failed, consecutiveFailures=" + std::to_string(consecutiveFailures));
        if (consecutiveFailures >= WATCHDOG_MAX_FAILURES) {
            LOG_ERROR("[Watchdog] Too many consecutive failures, bridge should exit");
            return false;
        }
    }
    return true;
}

int main(int argc, char* argv[]) {
    // 检查参数
    if (argc != 3) {
        printUsage(argv[0]);
        return 1;
    }
    
    std::string appSocketName = argv[1];
    std::string daemonSocketName = argv[2];
    
    std::cout << "Battery Service Socket Proxy" << std::endl;
    std::cout << "App socket: " << appSocketName << std::endl;
    std::cout << "Daemon socket: " << daemonSocketName << std::endl;
    std::cout << std::endl;
    
    // 初始化日志
    Logger::init(Config::BRIDGE_LOG_PATH);
    
    // 多实例检查
    if (!acquirePidLock()) {
        return 1;
    }

    // 使用 self-pipe 将异步信号转换为可 select 的 fd 事件。
    if (pipe(g_signalPipe) < 0) {
        std::cerr << "Failed to create signal pipe: " << strerror(errno) << std::endl;
        return 1;
    }
    int flags = fcntl(g_signalPipe[1], F_GETFL, 0);
    if (flags < 0 || fcntl(g_signalPipe[1], F_SETFL, flags | O_NONBLOCK) < 0) {
        std::cerr << "Failed to set signal pipe non-blocking: " << strerror(errno) << std::endl;
        close(g_signalPipe[0]);
        close(g_signalPipe[1]);
        g_signalPipe[0] = -1;
        g_signalPipe[1] = -1;
        return 1;
    }

    // 使用 sigaction 代替 signal()，明确指定 SA_RESTART 语义
    struct sigaction sa{};
    sa.sa_handler = signalHandler;
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = SA_RESTART;
    sigaction(SIGINT,  &sa, nullptr);
    sigaction(SIGTERM, &sa, nullptr);

    PidLockGuard pidGuard; // 离开 main 作用域时自动释放 PID 锁
    LOG_INFO("Bridge started, pid=" + std::to_string(getpid()));
    
    try {
        // 创建代理
        SocketProxy proxy(appSocketName, daemonSocketName, DAEMON_BINARY);
        
        // 启动代理
        if (!proxy.start()) {
            std::cerr << "Failed to start socket proxy" << std::endl;
            return 1;
        }
        
        std::cout << "Proxy started successfully, waiting for connections..." << std::endl;
        LOG_INFO("Proxy started, entering main loop with integrated watchdog");
        
        // 主循环 - proxy 运行的同时，定期执行 watchdog 检查
        // 这样即使 app 被 freezer 冻结（socket 不断开），bridge 仍能定期唤醒 app
        int watchdogFailures = 0;
        time_t lastWatchdogCheck = time(nullptr);
        
        while (g_running) {
            if (!proxy.isRunning()) {
                LOG_INFO("Proxy stopped running");
                break;
            }

            // select 等待 1s 或信号事件，收到 SIGTERM/SIGINT 时可立即醒来。
            fd_set readFds;
            FD_ZERO(&readFds);
            FD_SET(g_signalPipe[0], &readFds);
            struct timeval timeout{1, 0};
            int ret = select(g_signalPipe[0] + 1, &readFds, nullptr, nullptr, &timeout);
            if (ret > 0 && FD_ISSET(g_signalPipe[0], &readFds)) {
                char drain[32];
                while (read(g_signalPipe[0], drain, sizeof(drain)) > 0) {}
                if (!g_running) {
                    break;
                }
            } else if (ret < 0 && errno != EINTR) {
                LOG_WARN("Bridge main loop select failed: " + std::string(strerror(errno)));
            }
            
            // 检查是否到了 watchdog 检查时间
            time_t now = time(nullptr);
            if (now - lastWatchdogCheck >= WATCHDOG_INTERVAL_SEC) {
                lastWatchdogCheck = now;
                LOG_INFO("[Watchdog] Periodic check triggered");
                if (!watchdogCheck(watchdogFailures, proxy.getLastAppActivityTime())) {
                    // app 被 force-stop 或连续失败过多，退出
                    g_running = 0;
                    break;
                }
            }
        }
        
        // 停止代理
        proxy.stop();
        std::cout << "Proxy stopped" << std::endl;
        LOG_INFO("Main loop exited");

        if (g_signalPipe[0] >= 0) {
            close(g_signalPipe[0]);
            g_signalPipe[0] = -1;
        }
        if (g_signalPipe[1] >= 0) {
            close(g_signalPipe[1]);
            g_signalPipe[1] = -1;
        }
        
    } catch (const std::exception& e) {
        std::cerr << "Exception: " << e.what() << std::endl;
        LOG_ERROR("Exception: " + std::string(e.what()));
        if (g_signalPipe[0] >= 0) close(g_signalPipe[0]);
        if (g_signalPipe[1] >= 0) close(g_signalPipe[1]);
        return 1;
    }

    LOG_INFO("Bridge process exiting");
    return 0;
}
