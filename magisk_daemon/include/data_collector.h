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
#include <condition_variable>
#include <mutex>

enum class DataType {
    INT,
    STRING
};

struct ChargeConfig {
    int targetLimit;
    int actualLimit;
    bool chargingEnabled;

    ChargeConfig() : targetLimit(2000), actualLimit(2000), chargingEnabled(true) {}
};

class DataCollector {
private:
    static DataCollector instance;
    DataCollector();
    ~DataCollector();
    
    std::atomic<bool> running{false};
    std::atomic<bool> hasActiveClients{false};
    std::thread collectorThread;
    std::condition_variable cv;
    std::mutex cvMutex;
    
    // 采集间隔
    const std::chrono::milliseconds ACTIVE_INTERVAL{2000};
    const std::chrono::milliseconds CHARGING_INTERVAL{5000};
    const std::chrono::milliseconds DISCHARGING_INTERVAL{20000};
    
    // 充电状态监控
    int statusInotifyFd{-1};
    int statusWatchFd{-1};
    bool isCharging{true};
    static const std::string BATTERY_STATUS_PATH;
    
    // 缓存读取状态
    int consecutiveUnreadCount{0};
    static const int MAX_UNREAD_COUNT{3};
    
    // 充电控制相关
    ChargeConfig currentConfig;
    mutable std::mutex configMutex;
    int scenarioInotifyFd{-1};
    int scenarioWatchFd{-1};
    bool scenarioMonitoring{false};
    static const std::string SCENARIO_FCC_PATH;
    static const std::chrono::milliseconds WRITE_COOLDOWN;
    std::chrono::steady_clock::time_point lastWriteTime;
    bool isSelfWrite{false};
    
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
    
    // 充电控制私有方法
    bool writeFile(const std::string& path, const std::string& content);
    std::string readFile(const std::string& path);
    int readScenarioFcc();
    bool writeScenarioFcc(int value);
    bool checkAndRestoreLimit();
    
public:
    static DataCollector& getInstance() {
        return instance;
    }
    
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
    
    // 充电控制公共方法
    bool setChargeLimit(int limit);
    bool startScenarioMonitoring();
    void stopScenarioMonitoring();
    bool checkScenarioChange();
    int getScenarioInotifyFd() const { return scenarioInotifyFd; }
};

#endif // DATA_COLLECTOR_H