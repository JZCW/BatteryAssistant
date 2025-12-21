#include "charge_controller.h"
#include "logger.h"
#include <fstream>
#include <filesystem>
#include <vector>

ChargeController ChargeController::instance;

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

bool ChargeController::setChargeThreshold(int startThreshold, int endThreshold) {
    std::lock_guard<std::mutex> lock(configMutex);
    
    if (!isChargeControlSupported()) {
        LOG_WARN("Charge control not supported on this device");
        return false;
    }
    
    // 写入起始阈值
    std::string startPath = "/sys/class/power_supply/battery/charge_control_start_threshold";
    if (!writeFile(startPath, std::to_string(startThreshold))) {
        LOG_ERROR("Failed to set charge start threshold");
        return false;
    }
    
    // 写入结束阈值
    std::string endPath = "/sys/class/power_supply/battery/charge_control_end_threshold";
    if (!writeFile(endPath, std::to_string(endThreshold))) {
        LOG_ERROR("Failed to set charge end threshold");
        return false;
    }
    
    currentConfig.startThreshold = startThreshold;
    currentConfig.endThreshold = endThreshold;
    
    LOG_INFO("Charge threshold set: " + std::to_string(startThreshold) + 
              "-" + std::to_string(endThreshold));
    return true;
}

bool ChargeController::setChargeLimit(int limit) {
    std::lock_guard<std::mutex> lock(configMutex);
    
    if (!isChargeControlSupported()) {
        LOG_WARN("Charge control not supported on this device");
        return false;
    }
    
    std::string limitPath = "/sys/class/power_supply/battery/charge_control_limit";
    if (!writeFile(limitPath, std::to_string(limit))) {
        LOG_ERROR("Failed to set charge limit");
        return false;
    }
    
    currentConfig.limit = limit;
    
    LOG_INFO("Charge limit set: " + std::to_string(limit));
    return true;
}

bool ChargeController::enableCharging(bool enable) {
    std::lock_guard<std::mutex> lock(configMutex);
    
    // 尝试不同的充电控制文件路径
    std::vector<std::string> controlPaths = {
        "/sys/class/power_supply/battery/charging_enabled",
        "/sys/class/power_supply/battery/charge_control_enabled",
        "/sys/class/power_supply/usb/charging_enabled"
    };
    
    bool success = false;
    for (const auto& path : controlPaths) {
        if (std::filesystem::exists(path)) {
            if (writeFile(path, enable ? "1" : "0")) {
                currentConfig.chargingEnabled = enable;
                LOG_INFO("Charging " + std::string(enable ? "enabled" : "disabled") + 
                          " via " + path);
                success = true;
                break;
            }
        }
    }
    
    if (!success) {
        LOG_WARN("No charging control file found or all write attempts failed");
    }
    
    return success;
}

ChargeConfig ChargeController::getCurrentConfig() const {
    std::lock_guard<std::mutex> lock(configMutex);
    return currentConfig;
}

bool ChargeController::applyConfig(const ChargeConfig& config) {
    bool success = true;
    
    if (!setChargeThreshold(config.startThreshold, config.endThreshold)) {
        success = false;
    }
    
    if (!setChargeLimit(config.limit)) {
        success = false;
    }
    
    if (!enableCharging(config.chargingEnabled)) {
        success = false;
    }
    
    return success;
}

bool ChargeController::isChargeControlSupported() {
    // 检查是否存在充电控制文件
    std::vector<std::string> controlFiles = {
        "/sys/class/power_supply/battery/charge_control_start_threshold",
        "/sys/class/power_supply/battery/charge_control_end_threshold",
        "/sys/class/power_supply/battery/charge_control_limit"
    };
    
    for (const auto& file : controlFiles) {
        if (std::filesystem::exists(file)) {
            LOG_DEBUG("Charge control file found: " + file);
            return true;
        }
    }
    
    LOG_DEBUG("No charge control files found");
    return false;
}