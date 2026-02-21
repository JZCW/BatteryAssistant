#include "socket_proxy.h"
#include "logger.h"
#include <iostream>
#include <csignal>
#include <unistd.h>

volatile sig_atomic_t g_running = 1;

void signalHandler(int signal) {
    g_running = 0;
}

void printUsage(const char* programName) {
    std::cout << "Usage: " << programName << " <app_socket_path> <daemon_socket_name>" << std::endl;
    std::cout << "  app_socket_path:   Path to the socket file for app connection" << std::endl;
    std::cout << "  daemon_socket_name: Name of the abstract namespace socket for daemon connection" << std::endl;
    std::cout << std::endl;
    std::cout << "Example: " << programName << " /data/local/tmp/battery_service_app.sock battery_service" << std::endl;
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
    // Logger::init("/data/local/tmp/socket_proxy.log");
    
    try {
        // 创建代理
        SocketProxy proxy(appSocketPath, daemonSocketName);
        
        // 启动代理
        if (!proxy.start()) {
            std::cerr << "Failed to start socket proxy" << std::endl;
            return 1;
        }
        
        std::cout << "Proxy started successfully, waiting for connections..." << std::endl;
        
        // 主循环 - 等待代理停止
        while (proxy.isRunning() && g_running) {
            sleep(1);
        }
        
        // 停止代理
        proxy.stop();
        
        std::cout << "Proxy stopped" << std::endl;
        
    } catch (const std::exception& e) {
        std::cerr << "Exception: " << e.what() << std::endl;
        return 1;
    }
    
    return 0;
}
