#ifndef DATA_COLLECTOR_H
#define DATA_COLLECTOR_H

#include "battery_data.h"
#include <atomic>
#include <thread>
#include <chrono>
#include <vector>
#include <string>
#include <variant>
#include <sys/inotify.h>

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
    const std::chrono::milliseconds ACTIVE_INTERVAL{1000};     // 有客户端且数据被读取时1秒
    const std::chrono::milliseconds ACTIVE_IDLE_INTERVAL{5000}; // 有客户端但数据未读取时5秒
    const std::chrono::milliseconds CHARGING_INTERVAL{1000};  // 充电时1秒
    const std::chrono::milliseconds DISCHARGING_INTERVAL{5000}; // 放电时5秒
    
    // 充电状态监控
    int statusInotifyFd{-1};
    int statusWatchFd{-1};
    bool isCharging{true};
    static const std::string BATTERY_STATUS_PATH;
    
    // 缓存读取状态
    int consecutiveUnreadCount{0};
    static const int MAX_UNREAD_COUNT{3}; // 连续3次未读取后降低频率
    
    // 文件路径列表
    std::vector<std::string> batteryFiles;
    std::vector<std::string> usbFiles;
    std::vector<std::string> wirelessFiles;
    
    void collectLoop();
    BatteryData readAllFiles();
    template<typename T>
    void readFile(const std::string& path, T& target, DataType type);
    long getCurrentTimestamp();
    void updateChargingStatus();
    
public:
    DataCollector();
    ~DataCollector();
    
    void start();
    void stop();
    
    void onClientConnected();
    void onClientDisconnected();
    
    bool isRunning() const { return running; }
    bool hasClients() const { return hasActiveClients; }
    
    // 充电状态监控
    bool startStatusMonitoring();
    void stopStatusMonitoring();
    bool checkStatusChange();
    int getStatusInotifyFd() const { return statusInotifyFd; }
};

#endif // DATA_COLLECTOR_H