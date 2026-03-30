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

    ChargeConfig() : targetLimit(2500), actualLimit(2500), chargingEnabled(true) {}
};

class DataCollector {
private:
    static DataCollector instance;
    DataCollector();
    ~DataCollector();
    
    std::atomic<bool> running{false};
    std::thread collectorThread;
    std::condition_variable cv;
    std::mutex cvMutex;
    
    // 采集间隔
    const std::chrono::milliseconds CHARGING_INTERVAL{5000};
    const std::chrono::milliseconds DISCHARGING_INTERVAL{20000};
    
    // 充电状态监控
    int statusInotifyFd{-1};
    int statusWatchFd{-1};
    bool isCharging{true};
    const std::string BATTERY_STATUS_PATH = "/sys/class/power_supply/battery/status";
    
    // 刷新触发标记
    std::atomic<bool> dataIsStale{false};    // 充电状态变化时置位，绕过 WRITE_COOLDOWN
    std::condition_variable dataCv;          // 数据就绪通知（配套 dataCvMutex）
    std::mutex dataCvMutex;

    // 充电控制相关
    ChargeConfig currentConfig;
    mutable std::mutex configMutex;
    int scenarioInotifyFd{-1};
    int scenarioWatchFd{-1};
    bool scenarioMonitoring{false};
    const std::string SCENARIO_FCC_PATH = "/proc/charger/scenario_fcc";
    std::mutex updateMutex;
    const std::chrono::milliseconds WRITE_COOLDOWN{1000};
    std::chrono::steady_clock::time_point lastUpdateTime;
    
    // 文件路径列表
    std::vector<std::string> batteryFiles;
    std::vector<std::string> usbFiles;
    std::vector<std::string> wirelessFiles;
    
    void collectLoop();
    BatteryData readAllFiles();
    void updateData();
    template<typename T>
    void readFile(const std::string& path, T& target, DataType type);
    long getCurrentTimestamp();
    void updateChargingStatus(const std::string& status);
    
    // 充电控制私有方法
    bool writeFile(const std::string& path, const std::string& content);
    bool writeScenarioFcc(int value);
    bool applyLimitLocked(int requestedLimit, const std::string& reason);
    bool checkAndRestoreLimit(int currentValue);

    bool startStatusMonitoring();
    bool startScenarioMonitoring();
    void stopStatusMonitoring();
    void stopScenarioMonitoring();
    
public:
    static DataCollector& getInstance() {
        return instance;
    }
    
    bool start();
    void stop();
    
    void onClientConnected();
    void onClientDisconnected();
    
    bool isRunning() const { return running; }
    
    // 充电状态监控
    bool checkStatusChange(int fd);
    void onChargeStatusChanged(int fd); // 充电状态 inotify 事件：置位 stale 并唤醒循环
    bool notifyAppQuery(uint64_t versionBefore, std::chrono::milliseconds timeout);              // app 查询通知：置位 appRequested 并唤醒循环
    BatteryData getCurrentData();
    int getStatusInotifyFd() const { return statusInotifyFd; }
    
    // 充电控制公共方法
    bool setChargeLimit(int limit);
    int getScenarioInotifyFd() const { return scenarioInotifyFd; }
};

#endif // DATA_COLLECTOR_H