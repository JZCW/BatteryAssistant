#include "cache_manager.h"

CacheManager CacheManager::instance;

void CacheManager::updateBatteryData(const BatteryData& newData) {
    std::lock_guard<std::mutex> lock(dataMutex);
    
    // 检查是否有变化
    bool hasChanges = (currentData.timestamp == 0) || 
                     (newData.battery != currentData.battery) ||
                     (newData.usb != currentData.usb) ||
                     (newData.wireless != currentData.wireless);
    
    if (hasChanges) {
        currentData = newData;
        LOG_DEBUG("Battery data updated, timestamp: " + std::to_string(newData.timestamp));
    }
}

BatteryData CacheManager::getBatteryData() const {
    std::lock_guard<std::mutex> lock(dataMutex);
    return currentData;
}

long CacheManager::getTimestamp() const {
    std::lock_guard<std::mutex> lock(dataMutex);
    return currentData.timestamp;
}

bool CacheManager::isEmpty() const {
    std::lock_guard<std::mutex> lock(dataMutex);
    return currentData.timestamp == 0;
}