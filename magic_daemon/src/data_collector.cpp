#include "data_collector.h"
#include "cache_manager.h"
#include "logger.h"
#include <fstream>
#include <filesystem>
#include <thread>
#include <chrono>

DataCollector::DataCollector() {
    // 初始化文件路径列表
    batteryFiles = {
        "/sys/class/power_supply/battery/capacity",
        "/sys/class/power_supply/battery/temp",
        "/sys/class/power_supply/battery/voltage_now",
        "/sys/class/power_supply/battery/current_now",
        "/sys/class/power_supply/battery/status",
        "/sys/class/power_supply/battery/health",
        "/sys/class/power_supply/battery/charge_counter",
        "/sys/class/power_supply/battery/charge_full",
        "/sys/class/power_supply/battery/charge_full_design",
        "/sys/class/power_supply/battery/cycle_count",
        "/sys/class/power_supply/battery/time_to_empty_avg",
        "/sys/class/power_supply/battery/time_to_full_avg",
        "/sys/class/power_supply/battery/time_to_full_now",
        "/sys/class/power_supply/battery/charge_control_start_threshold",
        "/sys/class/power_supply/battery/charge_control_end_threshold",
        "/sys/class/power_supply/battery/charge_control_limit",
        "/sys/class/power_supply/battery/charge_control_limit_max",
        "/sys/class/power_supply/battery/technology",
        "/sys/class/power_supply/battery/model_name",
        "/sys/class/power_supply/battery/status",
        "/sys/class/power_supply/battery/health",
        "/sys/class/power_supply/battery/charge_type",
        "/sys/class/power_supply/battery/present"
    };
    
    usbFiles = {
        "/sys/class/power_supply/usb/online",
        "/sys/class/power_supply/usb/voltage_now",
        "/sys/class/power_supply/usb/voltage_max",
        "/sys/class/power_supply/usb/current_now",
        "/sys/class/power_supply/usb/current_max",
        "/sys/class/power_supply/usb/input_current_limit",
        "/sys/class/power_supply/usb/temp",
        "/sys/class/power_supply/usb/usb_type"
    };
    
    wirelessFiles = {
        "/sys/class/power_supply/wireless/online",
        "/sys/class/power_supply/wireless/voltage_now",
        "/sys/class/power_supply/wireless/voltage_max",
        "/sys/class/power_supply/wireless/current_now",
        "/sys/class/power_supply/wireless/current_max",
        "/sys/class/power_supply/wireless/input_current_limit",
        "/sys/class/power_supply/wireless/temp"
    };
}

DataCollector::~DataCollector() {
    stop();
}

void DataCollector::start() {
    if (running) return;
    
    running = true;
    collectorThread = std::thread(&DataCollector::collectLoop, this);
    LOG_INFO("DataCollector started");
}

void DataCollector::stop() {
    if (!running) return;
    
    running = false;
    if (collectorThread.joinable()) {
        collectorThread.join();
    }
    LOG_INFO("DataCollector stopped");
}

void DataCollector::onClientConnected() {
    hasActiveClients = true;
    LOG_INFO("Client connected, switching to 1s collection interval");
}

void DataCollector::onClientDisconnected() {
    hasActiveClients = false;
    LOG_INFO("All clients disconnected, switching to 5s collection interval");
}

void DataCollector::collectLoop() {
    while (running) {
        auto startTime = std::chrono::steady_clock::now();
        
        try {
            // 采集数据
            BatteryData data = readAllFiles();
            CacheManager::getInstance().updateBatteryData(data);
            
            // 记录采集间隔变化
            static bool lastClientState = false;
            if (lastClientState != hasActiveClients) {
                LOG_INFO("Collection interval changed to " + 
                         std::string(hasActiveClients ? "1s" : "5s"));
                lastClientState = hasActiveClients;
            }
            
        } catch (const std::exception& e) {
            LOG_ERROR("Data collection error: " + std::string(e.what()));
        }
        
        // 根据客户端状态决定采集间隔
        auto interval = hasActiveClients ? ACTIVE_INTERVAL : IDLE_INTERVAL;
        auto elapsed = std::chrono::steady_clock::now() - startTime;
        auto sleepTime = interval - elapsed;
        
        if (sleepTime.count() > 0) {
            std::this_thread::sleep_for(sleepTime);
        }
    }
}

BatteryData DataCollector::readAllFiles() {
    BatteryData data;
    
    // 读取电池文件
    for (const auto& file : batteryFiles) {
        std::string content = readFile(file);
        if (!content.empty()) {
            data.battery[getFileName(file)] = content;
        }
    }
    
    // 读取USB文件
    for (const auto& file : usbFiles) {
        std::string content = readFile(file);
        if (!content.empty()) {
            data.usb[getFileName(file)] = content;
        }
    }
    
    // 读取无线充电文件
    for (const auto& file : wirelessFiles) {
        std::string content = readFile(file);
        if (!content.empty()) {
            data.wireless[getFileName(file)] = content;
        }
    }
    
    data.timestamp = getCurrentTimestamp();
    
    return data;
}

std::string DataCollector::readFile(const std::string& path) {
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

std::string DataCollector::getFileName(const std::string& path) {
    size_t pos = path.find_last_of('/');
    return (pos != std::string::npos) ? path.substr(pos + 1) : path;
}

long DataCollector::getCurrentTimestamp() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();
}