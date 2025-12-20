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
        SocketServer server("/data/local/tmp/battery_service.sock");
        
        // 设置客户端连接回调
        server.setClientConnectedCallback([&dataCollector]() {
            dataCollector.onClientConnected();
        });
        
        server.setClientDisconnectedCallback([&dataCollector]() {
            dataCollector.onClientDisconnected();
        });
        
        if (!server.start()) {
            LOG_ERROR("Failed to start socket server");
            return 1;
        }
        
        LOG_INFO("Battery Service Daemon started successfully");
        LOG_INFO("Socket: /data/local/tmp/battery_service.sock");
        LOG_INFO("Log: /data/local/tmp/battery_service.log");
        
        // 主循环
        while (running) {
            sleep(1); // 等待信号
        }
        
        // 清理
        LOG_INFO("Shutting down Battery Service Daemon...");
        server.stop();
        dataCollector.stop();
        
        LOG_INFO("Battery Service Daemon stopped");
        
    } catch (const std::exception& e) {
        LOG_ERROR("Exception: " + std::string(e.what()));
        return 1;
    }
    
    return 0;
}