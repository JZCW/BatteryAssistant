#include <iostream>
#include <signal.h>
#include <unistd.h>
#include "data_collector.h"
#include "cache_manager.h"
#include "socket_server.h"
#include "charge_controller.h"
#include "logger.h"

volatile sig_atomic_t running = 1;

void signalHandler(int signal) {
    running = 0;
}

int main() {
    signal(SIGINT, signalHandler);
    signal(SIGTERM, signalHandler);
    
    // 初始化日志
    Logger::init("/data/local/tmp/battery_service.log");
    LOG_INFO("Battery Service Daemon starting...");
    
    try {
        // 检查root权限
        if (getuid() != 0) {
            LOG_ERROR("Root permission required");
            return 1;
        }
        
        // 初始化组件
        CacheManager::getInstance();
        ChargeController::getInstance();
        
        // 启动数据采集器
        DataCollector dataCollector;
        dataCollector.start();
        
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
        while (running) {
            sleep(1); // 等待信号
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