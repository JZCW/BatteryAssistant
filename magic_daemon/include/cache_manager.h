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
    
    CacheManager() = default;
    
public:
    static CacheManager& getInstance() {
        return instance;
    }
    
    void updateBatteryData(const BatteryData& newData) {
        std::lock_guard<std::mutex> lock(dataMutex);
        
        // 检查是否有变化
        bool hasChanges = (currentData.timestamp == 0) || 
                         (newData.battery != currentData.battery) ||
                         (newData.usb != currentData.usb) ||
                         (newData.wireless != currentData.wireless);
        
        if (hasChanges) {
            currentData = newData;
        }
    }
    
    BatteryData getBatteryData() const {
        std::lock_guard<std::mutex> lock(dataMutex);
        return currentData;
    }
    
    long getTimestamp() const {
        std::lock_guard<std::mutex> lock(dataMutex);
        return currentData.timestamp;
    }
    
    bool isEmpty() const {
        std::lock_guard<std::mutex> lock(dataMutex);
        return currentData.timestamp == 0;
    }
};

#endif // CACHE_MANAGER_H