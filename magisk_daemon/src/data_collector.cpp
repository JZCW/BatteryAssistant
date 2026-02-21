#include "data_collector.h"
#include "cache_manager.h"
#include "logger.h"
#include <fstream>
#include <filesystem>
#include <thread>
#include <chrono>
#include <type_traits>
#include <unistd.h>

DataCollector DataCollector::instance;

DataCollector::DataCollector() : statusInotifyFd(-1), statusWatchFd(-1), isCharging(true), scenarioInotifyFd(-1), scenarioWatchFd(-1), scenarioMonitoring(false) {
    lastUpdateTime = std::chrono::steady_clock::now();
}

DataCollector::~DataCollector() {
    stop();
}

bool DataCollector::start() {
    if (running) return true;
    running = true;

    // 启动充电控制监控
    if (!startScenarioMonitoring()) {
        LOG_ERROR("Failed to start scenario monitoring");
        return false;
    }
    
    // 启动充电状态监控
    if (!startStatusMonitoring()) {
        LOG_ERROR("Failed to start status monitoring");
        return false;
    }

    collectorThread = std::thread(&DataCollector::collectLoop, this);

    LOG_INFO("DataCollector started");
    return true;
}

void DataCollector::stop() {
    if (!running) return;
    running = false;

    stopStatusMonitoring();
    stopScenarioMonitoring();

    cv.notify_one();
    
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
        
        // 检查缓存是否被读取
        bool wasRead = CacheManager::getInstance().wasDataRead();
        if (wasRead) {
            consecutiveUnreadCount = 0;
        } else {
            consecutiveUnreadCount++;
        }

        // 更新数据
        LOG_DEBUG("Updating data -- loop");
        updateData();

        // 根据客户端状态、充电状态和缓存读取状态决定采集间隔
        static bool lastChargingState = false;
        static bool lastReadState = false;
        bool currentReadState = (hasActiveClients && consecutiveUnreadCount < MAX_UNREAD_COUNT);

        std::chrono::milliseconds interval = currentReadState ? ACTIVE_INTERVAL : (isCharging ? CHARGING_INTERVAL : DISCHARGING_INTERVAL);
        if (lastChargingState != isCharging || lastReadState != currentReadState) {
            std::string intervalDesc = currentReadState ? "(active, data readed)" : (isCharging ? "(charging)" : "(discharging)");
            LOG_INFO("Collection interval changed to " + intervalDesc);
        }
        lastChargingState = isCharging;
        lastReadState = currentReadState;

        // 计算休眠时间
        auto elapsed = std::chrono::steady_clock::now() - startTime;
        auto sleepTime = interval - elapsed;
        
        if (sleepTime.count() > 0) {
            std::unique_lock<std::mutex> lock(cvMutex);
            cv.wait_for(lock, sleepTime, [this] { return !running; });
        }
    }
}

void DataCollector::updateData() {
    // 尝试获取锁，如果已被占用则直接返回（忽略重复调用）
    std::unique_lock<std::mutex> lock(updateMutex, std::defer_lock);
    if (!lock.try_lock()) {
        LOG_DEBUG("updateData already in progress, skipping");
        return;
    }

    // 检查距离上次更新是否超过冷却时间（1秒）
    auto now = std::chrono::steady_clock::now();
    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(now - lastUpdateTime);
    if (elapsed < WRITE_COOLDOWN) { 
        LOG_DEBUG("Skipping update due to cooldown");
        return; 
    }

    try {
        LOG_DEBUG("Updating battery data");
        // 采集数据
        BatteryData data = readAllFiles();
        CacheManager::getInstance().updateBatteryData(data);
        // 更新充电状态
        updateChargingStatus(data.status_str);
        //TODO 计算新的目标值
        // 检查并恢复充电限制（定期写入）
        checkAndRestoreLimit(data.scenario_fcc);
    } catch (const std::exception& e) {
        LOG_ERROR("Data collection error: " + std::string(e.what()));
    }

    // 更新时间
    lastUpdateTime = now;
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

void DataCollector::updateChargingStatus(const std::string& status) {
    if (status.empty()) {
        isCharging = false;
        return;
    }
    
    bool newChargingState = status.length() < 11; // 这里用长度简单判断是不是 discharging

    if (newChargingState != isCharging) {
        LOG_INFO("Charging status changed: " + std::string(newChargingState ? "Charging" : "Discharging"));
    }
    isCharging = newChargingState;
}

bool DataCollector::startStatusMonitoring() {
    if (statusInotifyFd >= 0) {
        LOG_WARN("Status monitoring already started");
        return true;
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

bool DataCollector::checkStatusChange(int fd) {
    if (fd < 0) {
        return false;
    }
    
    // 读取并清空 inotify 事件队列
    char buffer[1024];
    ssize_t len = read(fd, buffer, sizeof(buffer));
    if (len > 0) {
        // 检测到变化，检查并恢复限制，同时重新采集完整数据
        LOG_DEBUG("updateData called due to inotify change");
        updateData();
        return true;
    }
    
    return false;
}

bool DataCollector::writeFile(const std::string& path, const std::string& content) {
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

bool DataCollector::writeScenarioFcc(int value) {
    return writeFile(SCENARIO_FCC_PATH, std::to_string(value));
}

bool DataCollector::setChargeLimit(int limit) {
    std::lock_guard<std::mutex> lock(configMutex);

    // 设置目标值
    currentConfig.targetLimit = limit;

    // 设置实际值
    currentConfig.actualLimit = limit;

    LOG_INFO("Charge limit set: target=" + std::to_string(currentConfig.targetLimit) + 
              ", actual=" + std::to_string(currentConfig.actualLimit));

    // 写入实际值
    if (!writeScenarioFcc(currentConfig.actualLimit)) {
        LOG_ERROR("Failed to set charge limit to " + std::to_string(currentConfig.actualLimit));
        return false;
    }

    return true;
}

bool DataCollector::startScenarioMonitoring() {
    std::lock_guard<std::mutex> lock(configMutex);
    
    if (scenarioMonitoring) {
        LOG_WARN("Scenario monitoring already started");
        return true;
    }
    
    // 初始化 inotify
    scenarioInotifyFd = inotify_init1(IN_NONBLOCK);
    if (scenarioInotifyFd < 0) {
        LOG_ERROR("Failed to initialize inotify");
        return false;
    }
    
    // 添加监控
    scenarioWatchFd = inotify_add_watch(scenarioInotifyFd, SCENARIO_FCC_PATH.c_str(), IN_MODIFY);
    if (scenarioWatchFd < 0) {
        LOG_ERROR("Failed to add inotify watch");
        close(scenarioInotifyFd);
        scenarioInotifyFd = -1;
        return false;
    }
    
    scenarioMonitoring = true;
    LOG_INFO("Scenario monitoring started for " + SCENARIO_FCC_PATH);
    return true;
}

void DataCollector::stopScenarioMonitoring() {
    std::lock_guard<std::mutex> lock(configMutex);
    
    if (!scenarioMonitoring) {
        return;
    }
    
    if (scenarioWatchFd >= 0) {
        inotify_rm_watch(scenarioInotifyFd, scenarioWatchFd);
        scenarioWatchFd = -1;
    }
    
    if (scenarioInotifyFd >= 0) {
        close(scenarioInotifyFd);
        scenarioInotifyFd = -1;
    }
    
    scenarioMonitoring = false;
    LOG_INFO("Scenario monitoring stopped");
}

bool DataCollector::checkAndRestoreLimit(int currentValue) {
    std::lock_guard<std::mutex> lock(configMutex);
    
    if (!scenarioMonitoring) {
        return false;
    }
    
    // 读取当前值
    if (currentValue < 0) {
        LOG_ERROR("Failed to read scenario_fcc");
        return false;
    }
    
    // 充电时强制写入目标值（即使当前值相同）
    bool needWrite = false;
    if (currentValue != currentConfig.actualLimit) {
        needWrite = true;
        LOG_INFO("scenario_fcc changed from " + std::to_string(currentConfig.actualLimit) + 
                  " to " + std::to_string(currentValue) + ", restoring to " + std::to_string(currentConfig.actualLimit));
    } else if (isCharging) {
        needWrite = true;
    }
    
    if (needWrite) {
        // 写入目标值
        if (!writeScenarioFcc(currentConfig.actualLimit)) {
            LOG_ERROR("Failed to write scenario_fcc");
            return false;
        }
    }
    
    return true;
}
