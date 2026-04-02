#include "data_collector.h"
#include "logger.h"
#include <fstream>
#include <filesystem>
#include <thread>
#include <chrono>
#include <type_traits>
#include <unistd.h>
#include <climits>

namespace {
constexpr int LOW_BATTERY_THRESHOLD = 30;
constexpr int LOW_BATTERY_MIN_LIMIT = 2000;
constexpr int DISCONNECTED_DEFAULT_LIMIT = 2500;
}

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
        // 回滚 running 标志，允许外部重试
        running = false;
        return false;
    }
    
    // 启动充电状态监控
    if (!startStatusMonitoring()) {
        LOG_ERROR("Failed to start status monitoring");
        // 回滚已启动的 scenario 监控，避免后续 stop() 状态不一致
        stopScenarioMonitoring();
        running = false;
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

    cv.notify_all();
    
    if (collectorThread.joinable()) {
        collectorThread.join();
    }
    LOG_INFO("DataCollector stopped");
}

void DataCollector::onClientConnected() {
    LOG_INFO("Client connected");
}

void DataCollector::onClientDisconnected() {
    std::lock_guard<std::mutex> lock(configMutex);
    if (!applyLimitLocked(DISCONNECTED_DEFAULT_LIMIT, "client disconnected")) {
        LOG_ERROR("Failed to apply default charge limit after client disconnected");
    }
    LOG_INFO("All clients disconnected");
}

void DataCollector::collectLoop() {
    while (running) {
        // 更新数据
        updateData();

        // 根据充电状态决定采集间隔：
        // Atomic load avoids cross-thread data race on charge state.
        std::chrono::milliseconds interval = isCharging.load() ? CHARGING_INTERVAL : DISCHARGING_INTERVAL;
        LOG_INFO("Collection interval: " + std::to_string(interval.count()) + "ms ");

        // 等待期间可被 getCurrentData 唤醒
        std::unique_lock<std::mutex> lock(cvMutex);
        cv.wait_for(lock, interval);
    }
}

void DataCollector::updateData() {
    // 尝试获取锁，如果已被占用则直接返回（忽略重复调用）
    std::unique_lock<std::mutex> lock(updateMutex, std::defer_lock);
    if (!lock.try_lock()) {
        return;
    }
    // 检查距离上次更新是否超过冷却时间（1秒）
    // dataIsStale=true（充电状态变化）时绕过冷却，确保立即采集新鲜数据
    auto now = std::chrono::steady_clock::now();
    auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(now - lastUpdateTime);
    if (!dataIsStale.load(std::memory_order_acquire) && elapsed < WRITE_COOLDOWN) {
        LOG_DEBUG("updateData: skipping, cooldown not expired (" +
                  std::to_string(elapsed.count()) + "ms < " +
                  std::to_string(WRITE_COOLDOWN.count()) + "ms)");
        return;
    }

    try {
        // 采集数据
        BatteryData data = readAllFiles();
        {
            std::lock_guard<std::mutex> lock(dataMutex);
            currentData = data;
            LOG_DEBUG("Battery data updated, timestamp: " + std::to_string(data.timestamp));
        }
        // 成功采集后清除 stale 标记
        dataIsStale.store(false, std::memory_order_release);

        // 尽快唤醒查询线程
        dataCv.notify_all();

        // 更新充电状态
        updateChargingStatus(data.status_str);
        {
            std::lock_guard<std::mutex> configLock(configMutex);
            applyLimitLocked(currentConfig.targetLimit, "safety rule");
        }

        // 检查并恢复充电限制
        checkAndRestoreLimit();
    } catch (const std::exception& e) {
        LOG_ERROR("Data collection error: " + std::string(e.what()));
        // 唤醒所有阻塞的查询线程，避免 stop 时死等
        dataCv.notify_all();
    }

    // 更新时间
    lastUpdateTime = now;
}

BatteryData DataCollector::readAllFiles() {
    BatteryData data;

    // 读取电池文件
    readFile("/proc/charger/real_soc", data.capacity, DataType::INT);
    readFile("/sys/class/power_supply/battery/voltage_now", data.voltage_now, DataType::INT);
    if (data.voltage_now != -1) data.voltage_now /= 1000;
    readFile("/sys/class/power_supply/battery/voltage_max", data.voltage_max, DataType::INT);
    if (data.voltage_max != -1) data.voltage_max /= 1000;
    readFile("/sys/class/power_supply/battery/voltage_ocv", data.voltage_ocv, DataType::INT);
    if (data.voltage_ocv != -1) data.voltage_ocv /= 1000;
    readFile("/sys/class/power_supply/battery/current_now", data.current_now, DataType::INT);
    if (data.current_now != 0) data.current_now /= 1000;
    readFile("/sys/class/power_supply/battery/current_avg", data.current_avg, DataType::INT);
    if (data.current_avg != 0) data.current_avg /= 1000;
    readFile("/sys/class/power_supply/battery/temp", data.temp_battery, DataType::INT);
    // readFile("/sys/class/power_supply/usb/temp", data.temp_usb, DataType::INT);
    // readFile("/proc/charger/usb_temp_gpio", data.temp_usb_gpio, DataType::INT);
    readFile("/proc/charger/battery_health", data.health, DataType::INT);
    readFile("/sys/class/power_supply/battery/status", data.status_str, DataType::STRING);
    readFile("/sys/class/power_supply/battery/charge_type", data.charge_type_str, DataType::STRING);
    readFile("/sys/class/power_supply/battery/charge_counter", data.charge_counter, DataType::INT);
    if (data.charge_counter != -1) data.charge_counter /= 1000;
    readFile("/sys/class/power_supply/battery/cycle_count", data.cycle_count, DataType::INT);
    readFile("/proc/charger/nt_quse", data.charge_full, DataType::INT);
    if (data.charge_full != -1) data.charge_full /= 1000;
    readFile("/proc/charger/nt_qmax", data.charge_design, DataType::INT);
    if (data.charge_design != -1) data.charge_design /= 1000;
    if (data.usb_voltage_now != -1) data.usb_voltage_now /= 1000;
    readFile("/sys/class/power_supply/usb/voltage_max", data.usb_voltage_max, DataType::INT);
    if (data.usb_voltage_max != -1) data.usb_voltage_max /= 1000;
    readFile("/sys/class/power_supply/usb/current_now", data.in_current_now, DataType::INT);
    if (data.in_current_now != -1) data.in_current_now /= 1000;
    readFile("/sys/class/power_supply/usb/current_max", data.usb_current_max, DataType::INT);
    if (data.usb_current_max != -1) data.usb_current_max /= 1000;
    readFile("/sys/class/power_supply/usb/voltage_max", data.usb_voltage_max, DataType::INT);
    readFile("/sys/class/power_supply/usb/current_now", data.in_current_now, DataType::INT);
    readFile("/sys/class/power_supply/usb/current_max", data.usb_current_max, DataType::INT);
    if (data.wireless_voltage_now != -1) data.wireless_voltage_now /= 1000;
    readFile("/sys/class/power_supply/wireless/voltage_max", data.wireless_voltage_max, DataType::INT);
    if (data.wireless_voltage_max != -1) data.wireless_voltage_max /= 1000;
    readFile("/sys/class/power_supply/wireless/current_max", data.wireless_current_max, DataType::INT);
    if (data.wireless_current_max != -1) data.wireless_current_max /= 1000;
    readFile("/sys/class/power_supply/wireless/online", data.wireless_online, DataType::INT);
    readFile("/sys/class/power_supply/wireless/voltage_now", data.wireless_voltage_now, DataType::INT);
    readFile("/sys/class/power_supply/wireless/voltage_max", data.wireless_voltage_max, DataType::INT);
    readFile("/sys/class/power_supply/wireless/current_max", data.wireless_current_max, DataType::INT);
    readFile("/sys/class/qcom-battery/wireless_type", data.wireless_type, DataType::STRING);
    // readFile("/sys/class/qcom-battery/wireless_boost_en", data.wireless_boost_en, DataType::INT);
    if (data.scenario_fcc != -1) data.scenario_fcc /= 1000;
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
    
    // 安全去除首尾空白：先判断是否全为空白，避免 find_last_not_of 返回 npos
    // 后直接 +1 造成无符号溢出绕回（npos+1=0）的隐性依赖。
    // 只有确认存在非空白字符后，第二次 erase 的 pos 才保证合法。
    size_t trimFirst = content.find_first_not_of(" \t\n\r");
    if (trimFirst == std::string::npos) {
        content.clear();
    } else {
        content.erase(0, trimFirst);
        content.erase(content.find_last_not_of(" \t\n\r") + 1);
    }
    
    if (content.empty()) {
        return;
    }
    
    // 根据 DataType 执行不同的转换
    if (type == DataType::INT) {
        if constexpr (std::is_same_v<T, int>) {
            try {
                target = std::stoi(content);
            } catch (const std::out_of_range&) {
                // 数值超出 int 范围，保留字段默认值，避免整次采集失败
                LOG_WARN("readFile: value out of range for " + path + ": " + content);
            } catch (const std::invalid_argument&) {
                // 内容不是有效整数，保留字段默认值
                LOG_WARN("readFile: invalid integer for " + path + ": " + content);
            }
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
        isCharging.store(false);
        return;
    }
    
    bool newChargingState = status.length() < 11; // 这里用长度简单判断是不是 discharging

    if (newChargingState != isCharging.load()) {
        LOG_INFO("Charging status changed: " + std::string(newChargingState ? "Charging" : "Discharging"));
    }
    isCharging.store(newChargingState);
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

bool DataCollector::checkScenarioChange(int fd) {
    if (fd < 0) {
        return false;
    }

    // 读取并清空 inotify 事件队列
    // 缓冲区大小必须勿截断单个 inotify_event 结构体
    char buffer[sizeof(struct inotify_event) + NAME_MAX + 1];
    ssize_t len = read(fd, buffer, sizeof(buffer));
    if (len > 0) {
        // 检测到变化（场景文件），走正常 updateData 路径
        updateData();
        return true;
    }

    return false;
}

void DataCollector::onChargeStatusChanged(int fd) {
    if (fd < 0) return;

    // 清空 inotify 事件队列
    // 缓冲区大小必须勿截断单个 inotify_event 结构体
    char buffer[sizeof(struct inotify_event) + NAME_MAX + 1];
    ssize_t len = read(fd, buffer, sizeof(buffer));
    if (len > 0) {
        // 充电状态文件变化：标记数据过期
        LOG_INFO("Charging status file changed, marking data stale");
        // 使用 release 语义发布“数据已过期”信号，供采集线程 acquire 读取。
        dataIsStale.store(true, std::memory_order_release);
    }
}

BatteryData DataCollector::getCurrentData() {
    cv.notify_one();

    std::unique_lock<std::mutex> lk(dataCvMutex);
    dataCv.wait_for(lk, std::chrono::milliseconds(300));
    lk.unlock(); // 等待完成后先释放 dataCvMutex，避免与 dataMutex 嵌套持锁。
    std::lock_guard<std::mutex> lock(dataMutex);
    return currentData;
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

bool DataCollector::applyLimitLocked(int requestedLimit, const std::string& reason) {
    int capacity = -1;
    {
        std::lock_guard<std::mutex> lock(dataMutex);
        capacity = currentData.capacity;
    }

    currentConfig.targetLimit = requestedLimit;
    if (capacity < LOW_BATTERY_THRESHOLD && requestedLimit < LOW_BATTERY_MIN_LIMIT) { // 保守判断，电量不可用时也生效
        currentConfig.actualLimit = LOW_BATTERY_MIN_LIMIT;
    } else {
        currentConfig.actualLimit = requestedLimit;
    }

    if (currentConfig.actualLimit != currentConfig.targetLimit) {
        LOG_WARN("Charge limit restricted by safety rule: reason=" + reason +
                 ", requested=" + std::to_string(currentConfig.targetLimit) +
                 ", actual=" + std::to_string(currentConfig.actualLimit) +
                 ", capacity=" + std::to_string(capacity) +
                 ", threshold=" + std::to_string(LOW_BATTERY_THRESHOLD));
    }

    if (!writeScenarioFcc(currentConfig.actualLimit)) {
        LOG_ERROR("Failed to set charge limit to " + std::to_string(currentConfig.actualLimit));
        return false;
    }

    return true;
}

bool DataCollector::setChargeLimit(int limit) {
    // Defensive check for internal callers as well.
    if (limit < 0) {
        LOG_WARN("Rejected negative charge limit: " + std::to_string(limit));
        return false;
    }

    std::lock_guard<std::mutex> lock(configMutex);
    return applyLimitLocked(limit, "app request");
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

bool DataCollector::checkAndRestoreLimit() {
    std::lock_guard<std::mutex> lock(configMutex);
    
    if (!scenarioMonitoring) {
        return false;
    }

    int scenario_fcc = -1;
    int current = 0;
    {
        std::lock_guard<std::mutex> dataLock(dataMutex);
        scenario_fcc = currentData.scenario_fcc;
        current = currentData.current_now;  // 已经是 mA，无需再 /1000
    }
    
    // 读取当前值
    if (scenario_fcc < 0) {
        LOG_ERROR("Failed to read scenario_fcc");
        return false;
    }
    
    // 写入目标值
    bool needWrite = false;
    if (scenario_fcc != currentConfig.actualLimit) {
        needWrite = true;
        LOG_INFO("scenario_fcc changed from " + std::to_string(currentConfig.actualLimit) + 
                  " to " + std::to_string(scenario_fcc) + ", restoring to " + std::to_string(currentConfig.actualLimit));
    } else if (isCharging.load() && (current > currentConfig.actualLimit)) { //当充电且电流大于预设值，强制写入
        needWrite = true;
        LOG_WARN("Current (" + std::to_string(current) + "mA) exceeds limit (" + std::to_string(currentConfig.actualLimit) + "mA), reapplying scenario_fcc");
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
