#include "socket_proxy.h"
#include "logger.h"
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

static const char* PID_FILE = "/data/local/tmp/batteryProxy.pid";
static int g_pidFileFd = -1;

volatile sig_atomic_t g_running = 1;

void signalHandler(int signal) {
    g_running = 0;
}

// 守护模式配置
static const int WATCHDOG_INTERVAL_SEC = 10 * 60; // 10分钟
static const int WATCHDOG_MAX_FAILURES = 3;       // 连续失败次数上限
static const char* APP_PACKAGE = "com.upo.batteryassistant";
static const char* APP_SERVICE = "com.upo.batteryassistant/.service.BatteryMonitorService";

// 尝试获取 PID 文件锁，防止多实例
static bool acquirePidLock() {
    g_pidFileFd = open(PID_FILE, O_CREAT | O_RDWR, 0644);
    if (g_pidFileFd < 0) {
        std::cerr << "Failed to open PID file: " << strerror(errno) << std::endl;
        return false;
    }

    // 尝试非阻塞独占锁
    if (flock(g_pidFileFd, LOCK_EX | LOCK_NB) < 0) {
        if (errno == EWOULDBLOCK) {
            // 读取已有 PID
            char buf[32] = {0};
            read(g_pidFileFd, buf, sizeof(buf) - 1);
            std::cerr << "Another bridge instance is running (pid=" << buf << "), exiting" << std::endl;
            LOG_WARN("Another bridge instance is running (pid=" + std::string(buf) + "), exiting");
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
    write(g_pidFileFd, pidStr.c_str(), pidStr.size());
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

void printUsage(const char* programName) {
    std::cout << "Usage: " << programName << " <app_socket_path> <daemon_socket_name>" << std::endl;
    std::cout << "  app_socket_path:   Path to the socket file for app connection" << std::endl;
    std::cout << "  daemon_socket_name: Name of the abstract namespace socket for daemon connection" << std::endl;
    std::cout << std::endl;
    std::cout << "Example: " << programName << " /data/local/tmp/battery_service_app.sock battery_service" << std::endl;
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
    cmd += " 2>/dev/null | grep -i 'stopped=true'";
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
    std::string output = execCommand(cmd);
    LOG_INFO("[Watchdog] am startservice result: " + output);
    // 检查是否包含错误
    if (output.find("Error") != std::string::npos ||
        output.find("Exception") != std::string::npos ||
        output.find("not found") != std::string::npos) {
        return false;
    }
    return true;
}

// 守护模式主循环
static void runWatchdog() {
    LOG_INFO("[Watchdog] Entering watchdog mode, interval=" + std::to_string(WATCHDOG_INTERVAL_SEC) + "s");
    int consecutiveFailures = 0;

    while (g_running) {
        // 等待指定间隔，但每秒检查一次 g_running 以便及时响应信号
        for (int i = 0; i < WATCHDOG_INTERVAL_SEC && g_running; ++i) {
            sleep(1);
        }
        if (!g_running) break;

        // 检查 app 是否被用户强行停止
        if (isAppForceStopped()) {
            LOG_INFO("[Watchdog] App is force-stopped by user, exiting watchdog");
            break;
        }

        bool processAlive = isAppProcessAlive();
        LOG_INFO("[Watchdog] Check: processAlive=" + std::string(processAlive ? "true" : "false"));

        // 尝试唤醒 app
        if (wakeUpApp()) {
            LOG_INFO("[Watchdog] Wake-up succeeded");
            consecutiveFailures = 0;
        } else {
            consecutiveFailures++;
            LOG_WARN("[Watchdog] Wake-up failed, consecutiveFailures=" + std::to_string(consecutiveFailures));
            if (consecutiveFailures >= WATCHDOG_MAX_FAILURES) {
                LOG_ERROR("[Watchdog] Too many consecutive failures, exiting watchdog");
                break;
            }
        }
    }

    LOG_INFO("[Watchdog] Watchdog mode exited");
}

int main(int argc, char* argv[]) {
    // 设置信号处理
    signal(SIGINT, signalHandler);
    signal(SIGTERM, signalHandler);
    
    // 检查参数
    if (argc != 3) {
        printUsage(argv[0]);
        return 1;
    }
    
    std::string appSocketPath = argv[1];
    std::string daemonSocketName = argv[2];
    
    std::cout << "Battery Service Socket Proxy" << std::endl;
    std::cout << "App socket: " << appSocketPath << std::endl;
    std::cout << "Daemon socket: " << daemonSocketName << std::endl;
    std::cout << std::endl;
    
    // 初始化日志（可选，用于调试）
    Logger::init("/data/local/tmp/socket_proxy.log");
    
    // 多实例检查
    if (!acquirePidLock()) {
        return 1;
    }
    LOG_INFO("Bridge started, pid=" + std::to_string(getpid()));
    
    try {
        // 创建代理
        SocketProxy proxy(appSocketPath, daemonSocketName);
        
        // 启动代理
        if (!proxy.start()) {
            std::cerr << "Failed to start socket proxy" << std::endl;
            return 1;
        }
        
        std::cout << "Proxy started successfully, waiting for connections..." << std::endl;
        LOG_INFO("Proxy started, entering proxy mode");
        
        // 主循环 - 等待代理停止
        while (proxy.isRunning() && g_running) {
            sleep(1);
        }
        
        // 停止代理
        proxy.stop();
        
        std::cout << "Proxy stopped" << std::endl;
        LOG_INFO("Proxy stopped, checking whether to enter watchdog mode");
        
        // 代理停止后，进入守护模式
        if (g_running) {
            runWatchdog();
        }
        
    } catch (const std::exception& e) {
        std::cerr << "Exception: " << e.what() << std::endl;
        LOG_ERROR("Exception: " + std::string(e.what()));
        return 1;
    }
    
    releasePidLock();
    LOG_INFO("Bridge process exiting");
    return 0;
}
