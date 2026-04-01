#include <iostream>
#include <signal.h>
#include <unistd.h>
#include <sys/select.h>
#include <fcntl.h>
#include <cstring>
#include "data_collector.h"
#include "socket_server.h"
#include "logger.h"
#include "config.h"

volatile sig_atomic_t running = 1;
static int g_signalPipe[2] = {-1, -1};

void signalHandler(int signal) {
    (void)signal;
    running = 0;
    // self-pipe 立即唤醒 select，避免仅靠 1s 超时退出。
    if (g_signalPipe[1] >= 0) {
        const char sig = 'x';
        ssize_t rc = write(g_signalPipe[1], &sig, 1);
        (void)rc;
    }
}

int main(int argc, char* argv[]) {
    // 建立 self-pipe，把异步信号转成可轮询 fd 事件。
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
    
    // 解析命令行参数
    bool debugMode = false;
    for (int i = 1; i < argc; i++) {
        if (std::string(argv[i]) == "--debug") {
            debugMode = true;
            break;
        }
    }
    
    // 初始化日志
    Logger::init(Config::DAEMON_LOG_PATH);
    Logger::setLevel(debugMode ? Logger::DEBUG : Logger::INFO);
    LOG_INFO("Battery Service Daemon starting...");
    if (debugMode) {
        LOG_INFO("Debug mode enabled");
    }
    
    try {
        // 检查root权限
        if (getuid() != 0) {
            LOG_ERROR("Root permission required");
            return 1;
        }

        // 启动数据采集器
        DataCollector& dataCollector = DataCollector::getInstance();
        if (!dataCollector.start()) {
            LOG_ERROR("Failed to start data collector");
            return 1;
        }
        
        // 启动Socket服务器
        LOG_INFO("Initializing SocketServer with abstract namespace...");
        SocketServer server("battery_service");
        
        // 设置客户端连接回调
        server.setClientConnectedCallback([&dataCollector]() {
            dataCollector.onClientConnected();
        });
        
        server.setClientDisconnectedCallback([&dataCollector]() {
            dataCollector.onClientDisconnected();
        });
        
        LOG_INFO("Starting socket server...");
        if (!server.start()) {
            LOG_ERROR("Failed to start socket server");
            return 1;
        }
        // start() 返回 true 即表示 bind+listen 已成功，无需额外等待

        LOG_INFO("Battery Service Daemon started successfully");
        LOG_INFO("Socket: abstract namespace 'battery_service'");
        LOG_INFO("Log: " + std::string(Config::DAEMON_LOG_PATH));
        LOG_INFO("Active clients: " + std::to_string(server.getActiveClients()));
        
        // 主循环
        LOG_INFO("Entering main event loop...");
        int loopCount = 0;
        int scenarioInotifyFd = dataCollector.getScenarioInotifyFd();
        int statusInotifyFd = dataCollector.getStatusInotifyFd();

        // 两路 inotify 均无效时无法监听任何事件，继续运行将退化为每秒空转
        if (scenarioInotifyFd < 0 && statusInotifyFd < 0) {
            LOG_ERROR("Both inotify file descriptors are invalid, cannot enter event loop");
            dataCollector.stop();
            server.stop();
            return 1;
        }

        while (running) {
            fd_set readfds;
            FD_ZERO(&readfds);

            // 监听信号管道，实现收到 SIGINT/SIGTERM 后即时唤醒。
            if (g_signalPipe[0] >= 0) {
                FD_SET(g_signalPipe[0], &readfds);
            }
            
            // 添加 inotify 文件描述符到 select 集合
            if (scenarioInotifyFd >= 0) {
                FD_SET(scenarioInotifyFd, &readfds);
            }
            if (statusInotifyFd >= 0) {
                FD_SET(statusInotifyFd, &readfds);
            }
            
            // 等待事件，超时 1 秒
            struct timeval timeout;
            timeout.tv_sec = 1;
            timeout.tv_usec = 0;
            
            int maxFd = 0;
            if (g_signalPipe[0] >= 0 && g_signalPipe[0] > maxFd) maxFd = g_signalPipe[0];
            if (scenarioInotifyFd >= 0 && scenarioInotifyFd > maxFd) maxFd = scenarioInotifyFd;
            if (statusInotifyFd >= 0 && statusInotifyFd > maxFd) maxFd = statusInotifyFd;
            maxFd++;
            
            int ready = select(maxFd, &readfds, nullptr, nullptr, &timeout);
            
            if (ready < 0) {
                if (errno == EINTR) {
                    continue; // 被信号中断
                }
                LOG_ERROR("select error: " + std::string(strerror(errno)));
                break;
            }

            if (g_signalPipe[0] >= 0 && FD_ISSET(g_signalPipe[0], &readfds)) {
                // 清空管道事件，防止后续 select 立即被历史字节反复唤醒。
                char drain[32];
                while (read(g_signalPipe[0], drain, sizeof(drain)) > 0) {}
                if (!running) {
                    break;
                }
            }
            
            // 处理场景监控 inotify 事件
            if (scenarioInotifyFd >= 0 && FD_ISSET(scenarioInotifyFd, &readfds)) {
                // 检查场景变化并重新采集完整数据
                dataCollector.checkScenarioChange(scenarioInotifyFd);
            }
            
            // 处理充电状态 inotify 事件
            if (statusInotifyFd >= 0 && FD_ISSET(statusInotifyFd, &readfds)) {
                // 充电状态变化：标记数据过期并唤醒采集循环（绕过 WRITE_COOLDOWN）
                dataCollector.onChargeStatusChanged(statusInotifyFd);
            }
            
            loopCount++;
            
            // 每60秒记录一次状态
            if (loopCount % 60 == 0) {
                LOG_INFO("Daemon status: running=" + std::string(running ? "true" : "false") +
                        ", active_clients=" + std::to_string(server.getActiveClients()));
            }
        }
        
        // 清理
        LOG_INFO("Shutting down Battery Service Daemon...");
        LOG_INFO("Stopping socket server...");
        server.stop();
        LOG_INFO("Stopping data collector...");
        dataCollector.stop();
        
        LOG_INFO("Battery Service Daemon stopped cleanly");

        if (g_signalPipe[0] >= 0) {
            close(g_signalPipe[0]);
            g_signalPipe[0] = -1;
        }
        if (g_signalPipe[1] >= 0) {
            close(g_signalPipe[1]);
            g_signalPipe[1] = -1;
        }
        
    } catch (const std::exception& e) {
        LOG_ERROR("Exception: " + std::string(e.what()));
        if (g_signalPipe[0] >= 0) {
            close(g_signalPipe[0]);
            g_signalPipe[0] = -1;
        }
        if (g_signalPipe[1] >= 0) {
            close(g_signalPipe[1]);
            g_signalPipe[1] = -1;
        }
        return 1;
    }
    
    return 0;
}