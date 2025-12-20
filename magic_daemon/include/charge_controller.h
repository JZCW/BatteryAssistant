#ifndef CHARGE_CONTROLLER_H
#define CHARGE_CONTROLLER_H

#include <string>
#include <mutex>

struct ChargeConfig {
    int startThreshold;
    int endThreshold;
    int limit;
    bool chargingEnabled;
    
    ChargeConfig() : startThreshold(20), endThreshold(80), limit(100), chargingEnabled(true) {}
};

class ChargeController {
private:
    static ChargeController instance;
    ChargeConfig currentConfig;
    mutable std::mutex configMutex;
    
    ChargeController() = default;
    
    bool writeFile(const std::string& path, const std::string& content);
    std::string readFile(const std::string& path);
    
public:
    static ChargeController& getInstance() {
        return instance;
    }
    
    bool setChargeThreshold(int startThreshold, int endThreshold);
    bool setChargeLimit(int limit);
    bool enableCharging(bool enable);
    
    ChargeConfig getCurrentConfig() const;
    bool applyConfig(const ChargeConfig& config);
    
    // 检查充电控制文件是否存在
    bool isChargeControlSupported();
};

#endif // CHARGE_CONTROLLER_H