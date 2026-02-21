#ifndef CHARGE_CONTROLLER_H
#define CHARGE_CONTROLLER_H

#include <string>
#include <mutex>
#include <sys/inotify.h>
#include <chrono>
#include "battery_data.h"

struct ChargeConfig {
    int targetLimit;      // 上级指令目标值
    int actualLimit;      // 实际设置的值
    bool chargingEnabled;

    ChargeConfig() : targetLimit(2000), actualLimit(2000), chargingEnabled(true) {}
};

class ChargeController {
private:
    static ChargeController instance;
    ChargeConfig currentConfig;
    mutable std::mutex configMutex;
    
    // inotify 相关
    int inotifyFd;
    int watchFd;
    bool monitoring;
    static const std::string SCENARIO_FCC_PATH;
    
    // 写入控制
    std::chrono::steady_clock::time_point lastWriteTime;
    bool isSelfWrite;
    static const std::chrono::milliseconds WRITE_COOLDOWN;
    
    ChargeController();
    ~ChargeController();
    
    bool writeFile(const std::string& path, const std::string& content);
    std::string readFile(const std::string& path);
    int readScenarioFcc();
    bool writeScenarioFcc(int value);
    
public:
    static ChargeController& getInstance() {
        return instance;
    }
    
    bool setChargeLimit(int limit);
   
    // 监控相关
    bool startMonitoring();
    void stopMonitoring();
    bool checkAndRestoreLimit();
    int getInotifyFd() const { return inotifyFd; }
};

#endif // CHARGE_CONTROLLER_H