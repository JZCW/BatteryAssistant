#include "charge_controller.h"
#include "logger.h"
#include "battery_data.h"
#include <fstream>
#include <filesystem>
#include <vector>
#include <unistd.h>

const std::string ChargeController::SCENARIO_FCC_PATH = "/proc/charger/scenario_fcc";

ChargeController ChargeController::instance;

ChargeController::ChargeController() : inotifyFd(-1), watchFd(-1), monitoring(false) {}

ChargeController::~ChargeController() {
    stopMonitoring();
}

bool ChargeController::writeFile(const std::string& path, const std::string& content) {
    std::ofstream file(path);
    if (!file.is_open()) {
        LOG_ERROR("Failed to open file for writing: " + path);
        return false;
    }
    
    file << content;
    file.close();
    
    LOG_DEBUG("Written to " + path + ": " + content);
    return true;
}

std::string ChargeController::readFile(const std::string& path) {
    std::ifstream file(path);
    if (!file.is_open()) {
        return "";
    }
    
    std::string content;
    std::getline(file, content);
    file.close();
    
    // 去除空白字符
    content.erase(0, content.find_first_not_of(" \t\n\r"));
    content.erase(content.find_last_not_of(" \t\n\r") + 1);
    
    return content;
}

int ChargeController::readScenarioFcc() {
    std::string content = readFile(SCENARIO_FCC_PATH);
    if (content.empty()) {
        return -1;
    }
    try {
        return std::stoi(content);
    } catch (...) {
        return -1;
    }
}

bool ChargeController::writeScenarioFcc(int value) {
    return writeFile(SCENARIO_FCC_PATH, std::to_string(value));
}

bool ChargeController::setChargeLimit(int limit) {
    std::lock_guard<std::mutex> lock(configMutex);
    
    // 检查文件是否存在
    if (!std::filesystem::exists(SCENARIO_FCC_PATH)) {
        LOG_ERROR("scenario_fcc file not found: " + SCENARIO_FCC_PATH);
        return false;
    }
    
    // 设置目标值
    currentConfig.targetLimit = limit;
    
    // 写入实际值
    if (!writeScenarioFcc(limit)) {
        LOG_ERROR("Failed to set charge limit to " + std::to_string(limit));
        return false;
    }
    
    currentConfig.actualLimit = limit;
    
    LOG_INFO("Charge limit set: target=" + std::to_string(limit) + 
              ", actual=" + std::to_string(limit));
    return true;
}

bool ChargeController::enableCharging(bool enable) {
    std::lock_guard<std::mutex> lock(configMutex);
    currentConfig.chargingEnabled = enable;
    LOG_INFO("Charging " + std::string(enable ? "enabled" : "disabled"));
    return true;
}

ChargeConfig ChargeController::getCurrentConfig() const {
    std::lock_guard<std::mutex> lock(configMutex);
    return currentConfig;
}

bool ChargeController::startMonitoring() {
    std::lock_guard<std::mutex> lock(configMutex);
    
    if (monitoring) {
        LOG_WARN("Monitoring already started");
        return true;
    }
    
    // 检查文件是否存在
    if (!std::filesystem::exists(SCENARIO_FCC_PATH)) {
        LOG_ERROR("scenario_fcc file not found, cannot start monitoring");
        return false;
    }
    
    // 初始化 inotify
    inotifyFd = inotify_init1(IN_NONBLOCK);
    if (inotifyFd < 0) {
        LOG_ERROR("Failed to initialize inotify");
        return false;
    }
    
    // 添加监控
    watchFd = inotify_add_watch(inotifyFd, SCENARIO_FCC_PATH.c_str(), IN_MODIFY);
    if (watchFd < 0) {
        LOG_ERROR("Failed to add inotify watch");
        close(inotifyFd);
    inotifyFd = -1;
        return false;
    }
    
    monitoring = true;
    LOG_INFO("Monitoring started for " + SCENARIO_FCC_PATH);
    return true;
}

void ChargeController::stopMonitoring() {
    std::lock_guard<std::mutex> lock(configMutex);
    
    if (!monitoring) {
        return;
    }
    
    if (watchFd >= 0) {
        inotify_rm_watch(inotifyFd, watchFd);
        watchFd = -1;
    }
    
    if (inotifyFd >= 0) {
        close(inotifyFd);
        inotifyFd = -1;
    }
    
    monitoring = false;
    LOG_INFO("Monitoring stopped");
}

bool ChargeController::checkAndRestoreLimit() {
    std::lock_guard<std::mutex> lock(configMutex);
    
    if (!monitoring) {
        return false;
    }
    
    // 读取当前值
    int currentValue = readScenarioFcc();
    if (currentValue < 0) {
        LOG_ERROR("Failed to read scenario_fcc");
        return false;
    }
    
    // 检查是否被修改
    if (currentValue != currentConfig.actualLimit) {
        LOG_WARN("scenario_fcc changed from " + std::to_string(currentConfig.actualLimit) + 
                  " to " + std::to_string(currentValue) + ", restoring to target " + 
                  std::to_string(currentConfig.targetLimit));
        
        // 恢复到目标值
        if (!writeScenarioFcc(currentConfig.targetLimit)) {
            LOG_ERROR("Failed to restore scenario_fcc");
            return false;
        }
        
        currentConfig.actualLimit = currentConfig.targetLimit;
        return true;
    }
    
    return false;
}

void ChargeController::applyChargeStrategy(const BatteryData& data) {
    // 扩展点：未来可以在这里实现复杂的充电策略
    // 例如：根据温度、电池健康度、使用场景等调整充电
    LOG_DEBUG("applyChargeStrategy called (extension point)");
}