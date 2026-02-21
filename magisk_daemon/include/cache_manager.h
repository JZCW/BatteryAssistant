#ifndef CACHE_MANAGER_H
#define CACHE_MANAGER_H

#include "battery_data.h"
#include <mutex>
#include <atomic>

class CacheManager {
private:
    static CacheManager instance;
    BatteryData currentData;
    mutable std::mutex dataMutex;
    mutable std::atomic<bool> dataReadSinceLastUpdate{false};
    
    CacheManager() = default;
    
public:
    static CacheManager& getInstance() {
        return instance;
    }
    
    void updateBatteryData(const BatteryData& newData);
    BatteryData getBatteryData(bool markRead = true) const;
    bool wasDataRead() const;
};

#endif // CACHE_MANAGER_H