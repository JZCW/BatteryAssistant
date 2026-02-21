#include "cache_manager.h"
#include "logger.h"

CacheManager CacheManager::instance;

void CacheManager::updateBatteryData(const BatteryData& newData) {
    std::lock_guard<std::mutex> lock(dataMutex);
    
    // 检查是否有变化
    bool hasChanges = (currentData.timestamp == 0) ||
                     (newData != currentData);
    
    if (hasChanges) {
        currentData = newData;
        // 重置读取标记
        dataReadSinceLastUpdate = false;
        LOG_DEBUG("Battery data updated, timestamp: " + std::to_string(newData.timestamp));
    }
}

BatteryData CacheManager::getBatteryData() const {
    std::lock_guard<std::mutex> lock(dataMutex);
    dataReadSinceLastUpdate.store(true);
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

void CacheManager::markDataRead() {
    dataReadSinceLastUpdate = true;
}

bool CacheManager::wasDataRead() const {
    return dataReadSinceLastUpdate;
}