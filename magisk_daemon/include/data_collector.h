#ifndef DATA_COLLECTOR_H
#define DATA_COLLECTOR_H

#include "battery_data.h"
#include "config.h"
#include <atomic>
#include <thread>
#include <chrono>
#include <vector>
#include <string>
#include <unordered_map>
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

    ChargeConfig() : targetLimit(2500), actualLimit(2500) {}
};

class DataCollector {
private:
    using IntFieldPtr = int BatteryData::*;
    using StringFieldPtr = std::string BatteryData::*;

    struct FieldSpec {
        const char* key;
        const char* path;
        DataType type;
        int scaleDivisor;
        bool isControlPath;
        std::variant<IntFieldPtr, StringFieldPtr> target;
    };

    struct DeviceProfile {
        std::string name{"generic"};
        std::string batteryStatusPath{Config::BATTERY_STATUS_PATH};
        std::string scenarioFccPath{Config::SCENARIO_FCC_PATH};
        std::vector<FieldSpec> fields;
    };

    DataCollector();
    ~DataCollector();
    
    std::atomic<bool> running{false};
    std::thread collectorThread;
    std::condition_variable cv;
    std::mutex cvMutex;
    
    // 采集间隔
    const std::chrono::milliseconds CHARGING_INTERVAL{5000};
    const std::chrono::milliseconds DISCHARGING_INTERVAL{20000};

    // 锁获取顺序约定：违反下列顺序嵌套持锁将导致死锁
    // configMutex → dataMutex（禁止逆序）
    // updateMutex 独立，以 try_lock 使用，不与其他锁嵌套
    // cvMutex     独立，仅用于 collectLoop 的采集间隔等待，不与其他锁嵌套
    // dataCvMutex 独立，仅用于 getCurrentData/dataCv 的数据就绪等待，不与业务锁嵌套
    // 数据缓存
    BatteryData currentData;
    mutable std::mutex dataMutex;
    
    // 充电状态监控
    int statusInotifyFd{-1};
    int statusWatchFd{-1};
    std::atomic<bool> isCharging{true};
    std::string batteryStatusPath{Config::BATTERY_STATUS_PATH};
    std::atomic<bool> statusPathUnavailable{false};
    
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
    std::string scenarioFccPath{Config::SCENARIO_FCC_PATH};
    std::atomic<bool> scenarioPathUnavailable{false};
    std::atomic<bool> scenarioWriteUnavailable{false};
    std::atomic<bool> scenarioUnavailableLogged{false};
    std::mutex updateMutex;
    const std::chrono::milliseconds WRITE_COOLDOWN{1000};
    std::chrono::steady_clock::time_point lastUpdateTime;
    DeviceProfile activeProfile;
    std::unordered_map<std::string, bool> readablePathCache;
    bool accessibilityProbed{false};
    mutable std::mutex accessibilityMutex;
    
    void collectLoop();
    BatteryData readAllFiles();
    void updateData();
    template<typename T>
    void readFile(const std::string& path, T& target, DataType type);
    void applyFieldSpec(const FieldSpec& spec, BatteryData& data);
    void probeAccessibilityOnce();
    bool isPathReadableCached(const std::string& path) const;
    std::string getSystemProperty(const char* key) const;
    std::string readDeviceFingerprint() const;
    DeviceProfile detectDeviceProfile(const std::string& fingerprint) const;
    std::vector<FieldSpec> buildGenericFieldSpecs() const;
    std::vector<FieldSpec> buildNothingFieldSpecs() const;
    long getCurrentTimestamp();
    void updateChargingStatus(const std::string& status);
    
    // 充电控制私有方法
    bool writeFile(const std::string& path, const std::string& content);
    bool writeScenarioFcc(int value);
    bool applyLimitLocked(int requestedLimit, const std::string& reason);
    bool checkAndRestoreLimit();

    bool startStatusMonitoring();
    bool startScenarioMonitoring();
    void stopStatusMonitoring();
    void stopScenarioMonitoring();
    
public:
    static DataCollector& getInstance() {
        // Meyers' Singleton：C++11 保证局部静态量线程安全初始化，
        // 析构由运行时管理，消除跨编译单元静态成员析构顺序不确定的风险。
        static DataCollector instance;
        return instance;
    }
    
    bool start();
    void stop();
    
    void onClientConnected();
    void onClientDisconnected();
    
    bool isRunning() const { return running; }
    
    // 充电状态监控
    bool checkScenarioChange(int fd);
    void onChargeStatusChanged(int fd);
    BatteryData getCurrentData();
    int getStatusInotifyFd() const { return statusInotifyFd; }
    
    // 充电控制公共方法
    bool setChargeLimit(int limit);
    int getScenarioInotifyFd() const { return scenarioInotifyFd; }
};

#endif // DATA_COLLECTOR_H