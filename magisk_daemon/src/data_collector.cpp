#include "data_collector.h"
#include "cache_manager.h"
#include "logger.h"
#include <fstream>
#include <filesystem>
#include <thread>
#include <chrono>
#include <type_traits>
#include <unistd.h>

const std::string DataCollector::BATTERY_STATUS_PATH = "/sys/class/power_supply/battery/status";

DataCollector::DataCollector() : statusInotifyFd(-1), statusWatchFd(-1), isCharging(true) {}

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
    if (collectorThread.joinable()) {  //TODO sleep期间可退出
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
            
            // 更新充电状态
            updateChargingStatus();
            
            // 检查缓存是否被读取 //FIXME 刚更新完就查？
            bool wasRead = CacheManager::getInstance().wasDataRead();
            if (wasRead) {
                consecutiveUnreadCount = 0;
            } else {
                consecutiveUnreadCount++;
            }
            
            // 记录采集间隔变化
            static bool lastClientState = false;
            static bool lastReadState = false;
            bool currentReadState = (hasActiveClients && consecutiveUnreadCount < MAX_UNREAD_COUNT);
            
            if (lastClientState != hasActiveClients || lastReadState != currentReadState) {
                std::string intervalDesc;
                if (hasActiveClients) {
                    if (consecutiveUnreadCount < MAX_UNREAD_COUNT) {
                        intervalDesc = "1s (active, data read)";
                    } else {
                        intervalDesc = "5s (active, data unread)";
                    }
                } else {
                    intervalDesc = isCharging ? "1s (charging)" : "5s (discharging)";
                }
                LOG_INFO("Collection interval changed to " + intervalDesc);
                lastClientState = hasActiveClients;
                lastReadState = currentReadState;
            }
            
        } catch (const std::exception& e) {
            LOG_ERROR("Data collection error: " + std::string(e.what()));
        }
        
        // 根据客户端状态、充电状态和缓存读取状态决定采集间隔
        std::chrono::milliseconds interval;
        if (hasActiveClients) {
            // 有客户端连接
            if (consecutiveUnreadCount < MAX_UNREAD_COUNT) {
                // 数据被读取过，保持高频率
                interval = ACTIVE_INTERVAL;
            } else {
                // 连续多次未读取，降低频率
                interval = ACTIVE_IDLE_INTERVAL;
            }
        } else {
            // 无客户端连接
            interval = isCharging ? CHARGING_INTERVAL : DISCHARGING_INTERVAL;
        }
        
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
    readFile("/proc/charger/real_soc", data.capacity, DataType::INT);
    readFile("/sys/class/power_supply/battery/voltage_now", data.voltage_now, DataType::INT);
    readFile("/sys/class/power_supply/battery/voltage_max", data.voltage_max, DataType::INT);
    readFile("/sys/class/power_supply/battery/voltage_ocv", data.voltage_ocv, DataType::INT);
    readFile("/sys/class/power_supply/battery/current_now", data.current_now, DataType::INT);
    readFile("/sys/class/power_supply/battery/current_avg", data.current_avg, DataType::INT);
    readFile("/sys/class/power_supply/battery/temp", data.temp_battery, DataType::INT);
    // readFile("/sys/class/power_supply/usb/temp", data.temp_usb, DataType::INT);
    // readFile("/proc/charger/usb_temp_gpio", data.temp_usb_gpio, DataType::INT);
    readFile("/proc/charger/battery_health", data.health, DataType::INT);
    readFile("/sys/class/power_supply/battery/status", data.status_str, DataType::STRING);
    readFile("/sys/class/power_supply/battery/charge_type", data.charge_type_str, DataType::STRING);
    readFile("/sys/class/power_supply/battery/charge_counter", data.charge_counter, DataType::INT);
    readFile("/sys/class/power_supply/battery/cycle_count", data.cycle_count, DataType::INT);
    readFile("/proc/charger/nt_quse", data.charge_full, DataType::INT);
    readFile("/proc/charger/nt_qmax", data.charge_design, DataType::INT);
    // readFile("/proc/charger/nt_resistance", data.battery_resistance, DataType::INT);
    readFile("/sys/class/power_supply/usb/online", data.usb_online, DataType::INT);
    readFile("/sys/class/power_supply/usb/voltage_now", data.usb_voltage_now, DataType::INT);
    readFile("/sys/class/power_supply/usb/voltage_max", data.usb_voltage_max, DataType::INT);
    readFile("/sys/class/power_supply/usb/current_now", data.in_current_now, DataType::INT);
    readFile("/sys/class/power_supply/usb/current_max", data.usb_current_max, DataType::INT);
    // readFile("/sys/class/power_supply/usb/input_current_limit", data.usb_input_current_limit, DataType::INT);
    readFile("/sys/class/power_supply/usb/usb_type", data.usb_type, DataType::STRING);
    readFile("/sys/class/power_supply/wireless/online", data.wireless_online, DataType::INT);
    readFile("/sys/class/power_supply/wireless/voltage_now", data.wireless_voltage_now, DataType::INT);
    readFile("/sys/class/power_supply/wireless/voltage_max", data.wireless_voltage_max, DataType::INT);
    readFile("/sys/class/power_supply/wireless/current_max", data.wireless_current_max, DataType::INT);
    readFile("/sys/class/qcom-battery/wireless_type", data.wireless_type, DataType::STRING);
    // readFile("/sys/class/qcom-battery/wireless_boost_en", data.wireless_boost_en, DataType::INT);
    // readFile("/sys/class/qcom-battery/wls_volt_tx", data.wls_tx_volt, DataType::INT);
    // readFile("/sys/class/qcom-battery/wls_curr_tx", data.wls_tx_curr, DataType::INT);
    // readFile("/sys/class/qcom-battery/wls_reverse_status", data.wls_rev_status, DataType::INT);
    // readFile("/sys/class/qcom-battery/wls_reverse_fod", data.wls_rev_fod, DataType::INT);
    // readFile("/sys/class/qcom-battery/restrict_chg", data.restrict_chg, DataType::INT);
    // readFile("/sys/class/qcom-battery/restrict_cur", data.restrict_cur, DataType::INT);
    readFile("/proc/charger/scenario_fcc", data.scenario_fcc, DataType::INT);
    // readFile("/proc/charger/nt_otg_enable", data.nt_otg_enable, DataType::INT);
    readFile("/proc/charger/nt_abnormal_status", data.nt_abnormal_status, DataType::INT);

    data.timestamp = getCurrentTimestamp();
    
    return data;
}

template<typename T>
void DataCollector::readFile(const std::string& path, T& target, DataType type) {
    std::ifstream file(path);
    if (!file.is_open()) {
        return;
    }
    
    std::string content;
    std::getline(file, content);
    file.close();
    
    // 去除空白字符
    content.erase(0, content.find_first_not_of(" \t\n\r"));
    content.erase(content.find_last_not_of(" \t\n\r") + 1);
    
    if (content.empty()) {
        return;
    }
    
    // 根据 DataType 执行不同的转换
    if (type == DataType::INT) {
        if constexpr (std::is_same_v<T, int>) {
            target = std::stoi(content);
        }
    } else if (type == DataType::STRING) {
        if constexpr (std::is_same_v<T, std::string>) {
            target = content;
        }
    }
}

long DataCollector::getCurrentTimestamp() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();
}

void DataCollector::updateChargingStatus() {
    std::string status;
    readFile(BATTERY_STATUS_PATH, status, DataType::STRING);
    if (status.empty()) {
        return;
    }
    
    bool newChargingState = status.length() < 11; // 这里用长度简单判断是不是 discharging

    if (newChargingState != isCharging) {
        LOG_INFO("Charging status changed: " + std::string(isCharging ? "Charging" : "Discharging"));
    }
    isCharging = newChargingState;
}

bool DataCollector::startStatusMonitoring() {
    if (statusInotifyFd >= 0) {
        LOG_WARN("Status monitoring already started");
        return true;
    }
    
    // 检查文件是否存在
    if (!std::filesystem::exists(BATTERY_STATUS_PATH)) {
        LOG_ERROR("Battery status file not found: " + BATTERY_STATUS_PATH);
        return false;
    }
    
    // 初始化 inotify
    statusInotifyFd = inotify_init1(IN_NONBLOCK);
    if (statusInotifyFd < 0) {
        LOG_ERROR("Failed to initialize inotify for status monitoring");
        return false;
    }
    
    // 添加监控
    statusWatchFd = inotify_add_watch(statusInotifyFd, BATTERY_STATUS_PATH.c_str(), IN_MODIFY);
    if (statusWatchFd < 0) {
        LOG_ERROR("Failed to add inotify watch for status monitoring");
        close(statusInotifyFd);
        statusInotifyFd = -1;
        return false;
    }
    
    // 初始读取状态
    updateChargingStatus();
    
    LOG_INFO("Status monitoring started for " + BATTERY_STATUS_PATH);
    return true;
}

void DataCollector::stopStatusMonitoring() {
    if (statusWatchFd >= 0) {
        inotify_rm_watch(statusInotifyFd, statusWatchFd);
        statusWatchFd = -1;
    }
    
    if (statusInotifyFd >= 0) {
        close(statusInotifyFd);
        statusInotifyFd = -1;
    }
    
    LOG_INFO("Status monitoring stopped");
}

bool DataCollector::checkStatusChange() {
    if (statusInotifyFd < 0) {
        return false;
    }
    
    // 读取并清空 inotify 事件队列
    char buffer[1024];
    ssize_t len = read(statusInotifyFd, buffer, sizeof(buffer));
    if (len > 0) {
        // 检测到变化，更新充电状态
        updateChargingStatus();
        return true;
    }
    
    return false;
}