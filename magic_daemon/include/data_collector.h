#ifndef DATA_COLLECTOR_H
#define DATA_COLLECTOR_H

#include "battery_data.h"
#include <atomic>
#include <thread>
#include <chrono>
#include <vector>
#include <string>
#include <variant>

enum class DataType {
    INT,
    STRING
};

class DataCollector {
private:
    std::atomic<bool> running{false};
    std::atomic<bool> hasActiveClients{false};
    std::thread collectorThread;
    
    // 采集间隔
    const std::chrono::milliseconds ACTIVE_INTERVAL{1000};  // 有客户端时1秒
    const std::chrono::milliseconds IDLE_INTERVAL{5000};   // 无客户端时5秒
    
    // 文件路径列表
    std::vector<std::string> batteryFiles;
    std::vector<std::string> usbFiles;
    std::vector<std::string> wirelessFiles;
    
    void collectLoop();
    BatteryData readAllFiles();
    template<typename T>
    void readFile(const std::string& path, T& target, DataType type);
    long getCurrentTimestamp();
    
public:
    DataCollector();
    ~DataCollector();
    
    void start();
    void stop();
    
    void onClientConnected();
    void onClientDisconnected();
    
    bool isRunning() const { return running; }
    bool hasClients() const { return hasActiveClients; }
};

#endif // DATA_COLLECTOR_H