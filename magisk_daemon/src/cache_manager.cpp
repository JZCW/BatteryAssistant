#include "cache_manager.h"
#include "logger.h"

CacheManager CacheManager::instance;

void CacheManager::updateBatteryData(const BatteryData& newData) {
    std::lock_guard<std::mutex> lock(dataMutex);

    currentData = newData;
    dataReadSinceLastUpdate = false; // 重置读取标记
    LOG_DEBUG("Battery data updated, timestamp: " + std::to_string(newData.timestamp));
}

BatteryData CacheManager::getBatteryData(bool markRead) const {
    std::lock_guard<std::mutex> lock(dataMutex);
    if (markRead) {
        dataReadSinceLastUpdate.store(true);
    }
    return currentData;
}

bool CacheManager::wasDataRead() const {
    return dataReadSinceLastUpdate;
}
