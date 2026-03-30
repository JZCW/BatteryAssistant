#include <iostream>
#include <signal.h>
#include <unistd.h>
#include <sys/select.h>
#include <cstring>
#include "data_collector.h"
#include "cache_manager.h"
#include "socket_server.h"
#include "logger.h"

volatile sig_atomic_t running = 1;

void signalHandler(int signal) {
    running = 0;
}

int main(int argc, char* argv[]) {
    signal(SIGINT, signalHandler);
    signal(SIGTERM, signalHandler);
    
    // 解析命令行参数
    bool debugMode = false;
    for (int i = 1; i < argc; i++) {
        if (std::string(argv[i]) == "--debug") {
            debugMode = true;
            break;
        }
    }
    
    // 初始化日志
    Logger::init("/data/local/tmp/battery_service.log");
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
        
        // 初始化组件
        CacheManager::getInstance();
        
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
        
        // 验证socket服务器状态
        sleep(1); // 给服务器一点时间完全启动
        if (!server.isRunning()) {
            LOG_ERROR("Socket server started but is not running");
            return 1;
        }
        
        LOG_INFO("Battery Service Daemon started successfully");
        LOG_INFO("Socket: abstract namespace 'battery_service'");
        LOG_INFO("Log: /data/local/tmp/battery_service.log");
        LOG_INFO("Active clients: " + std::to_string(server.getActiveClients()));
        
        // 主循环
        LOG_INFO("Entering main event loop...");
        int loopCount = 0;
        int scenarioInotifyFd = dataCollector.getScenarioInotifyFd();
        int statusInotifyFd = dataCollector.getStatusInotifyFd();
        
        while (running) {
            fd_set readfds;
            FD_ZERO(&readfds);
            
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
            
            // 处理场景监控 inotify 事件
            if (scenarioInotifyFd >= 0 && FD_ISSET(scenarioInotifyFd, &readfds)) {
                // 检查场景变化并重新采集完整数据
                dataCollector.checkStatusChange(scenarioInotifyFd);
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
        
    } catch (const std::exception& e) {
        LOG_ERROR("Exception: " + std::string(e.what()));
        return 1;
    }
    
    return 0;
}