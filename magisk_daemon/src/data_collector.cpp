#include "data_collector.h"
#include "logger.h"
#include <algorithm>
#include <cerrno>
#include <cctype>
#include <fstream>
#include <filesystem>
#include <thread>
#include <chrono>
#include <type_traits>
#include <unistd.h>
#include <climits>

#if __has_include(<sys/system_properties.h>)
#include <sys/system_properties.h>
#define BATTERY_ASSISTANT_HAS_ANDROID_PROPERTIES 1
#else
#define BATTERY_ASSISTANT_HAS_ANDROID_PROPERTIES 0
#endif

namespace {
constexpr int LOW_BATTERY_THRESHOLD = 30;
constexpr int LOW_BATTERY_MIN_LIMIT = 2000;
constexpr int DISCONNECTED_DEFAULT_LIMIT = 2500;

std::string toLowerCopy(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(),
                   [](unsigned char ch) { return static_cast<char>(std::tolower(ch)); });
    return value;
}
}

DataCollector::DataCollector() : statusInotifyFd(-1), statusWatchFd(-1), isCharging(true), scenarioInotifyFd(-1), scenarioWatchFd(-1), scenarioMonitoring(false) {
    lastUpdateTime = std::chrono::steady_clock::now();
}

DataCollector::~DataCollector() {
    stop();
}

std::string DataCollector::getSystemProperty(const char* key) const {
#if BATTERY_ASSISTANT_HAS_ANDROID_PROPERTIES
    char value[PROP_VALUE_MAX] = {0};
    if (__system_property_get(key, value) <= 0) {
        return "";
    }
    return value;
#else
    (void)key;
    return "";
#endif
}

std::string DataCollector::readDeviceFingerprint() const {
    return getSystemProperty("ro.build.fingerprint");
}

std::vector<DataCollector::FieldSpec> DataCollector::buildGenericFieldSpecs() const {
    return {
        {"capacity", "/sys/class/power_supply/battery/capacity", DataType::INT, 1, false, &BatteryData::capacity},
        {"voltage_now", "/sys/class/power_supply/battery/voltage_now", DataType::INT, 1000, false, &BatteryData::voltage_now},
        {"voltage_max", "/sys/class/power_supply/battery/voltage_max", DataType::INT, 1000, false, &BatteryData::voltage_max},
        {"voltage_ocv", "/sys/class/power_supply/battery/voltage_ocv", DataType::INT, 1000, false, &BatteryData::voltage_ocv},
        {"current_now", "/sys/class/power_supply/battery/current_now", DataType::INT, 1000, false, &BatteryData::current_now},
        {"current_avg", "/sys/class/power_supply/battery/current_avg", DataType::INT, 1000, false, &BatteryData::current_avg},
        {"temp_battery", "/sys/class/power_supply/battery/temp", DataType::INT, 1, false, &BatteryData::temp_battery},
        // {"temp_usb", "/sys/class/power_supply/usb/temp", DataType::INT, 1, false, &BatteryData::temp_usb},
        {"status_str", "/sys/class/power_supply/battery/status", DataType::STRING, 1, false, &BatteryData::status_str},
        {"charge_type_str", "/sys/class/power_supply/battery/charge_type", DataType::STRING, 1, false, &BatteryData::charge_type_str},
        {"charge_counter", "/sys/class/power_supply/battery/charge_counter", DataType::INT, 1000, false, &BatteryData::charge_counter},
        {"cycle_count", "/sys/class/power_supply/battery/cycle_count", DataType::INT, 1, false, &BatteryData::cycle_count},
        {"usb_online", "/sys/class/power_supply/usb/online", DataType::INT, 1, false, &BatteryData::usb_online},
        {"usb_voltage_now", "/sys/class/power_supply/usb/voltage_now", DataType::INT, 1000, false, &BatteryData::usb_voltage_now},
        {"usb_voltage_max", "/sys/class/power_supply/usb/voltage_max", DataType::INT, 1000, false, &BatteryData::usb_voltage_max},
        {"in_current_now", "/sys/class/power_supply/usb/current_now", DataType::INT, 1000, false, &BatteryData::in_current_now},
        {"usb_current_max", "/sys/class/power_supply/usb/current_max", DataType::INT, 1000, false, &BatteryData::usb_current_max},
        // {"usb_input_current_limit", "/sys/class/power_supply/usb/input_current_limit", DataType::INT, 1, false, &BatteryData::usb_input_current_limit},
        {"usb_type", "/sys/class/power_supply/usb/usb_type", DataType::STRING, 1, false, &BatteryData::usb_type},
        {"wireless_online", "/sys/class/power_supply/wireless/online", DataType::INT, 1, false, &BatteryData::wireless_online},
        {"wireless_voltage_now", "/sys/class/power_supply/wireless/voltage_now", DataType::INT, 1000, false, &BatteryData::wireless_voltage_now},
        {"wireless_voltage_max", "/sys/class/power_supply/wireless/voltage_max", DataType::INT, 1000, false, &BatteryData::wireless_voltage_max},
        {"wireless_current_max", "/sys/class/power_supply/wireless/current_max", DataType::INT, 1000, false, &BatteryData::wireless_current_max},
        // {"wireless_boost_en", "/sys/class/qcom-battery/wireless_boost_en", DataType::INT, 1, false, &BatteryData::wireless_boost_en},
        // {"wls_tx_volt", "/sys/class/qcom-battery/wls_volt_tx", DataType::INT, 1 , false, &BatteryData::wls_tx_volt};
        // {"wls_tx_curr", "/sys/class/qcom-battery/wls_curr_tx", DataType::INT, 1 , false, &BatteryData::wls_tx_curr};
        // {"wls_rev_status", "/sys/class/qcom-battery/wls_reverse_status", DataType::INT, 1 , false, &BatteryData::wls_rev_status};
        // {"wls_rev_fod", "/sys/class/qcom-battery/wls_reverse_fod", DataType::INT, 1 , false, &BatteryData::wls_rev_fod};
        // {"restrict_chg", "/sys/class/qcom-battery/restrict_chg", DataType::INT, 1 , false, &BatteryData::restrict_chg};
        // {"restrict_cur", "/sys/class/qcom-battery/restrict_cur", DataType::INT, 1 , false, &BatteryData::restrict_cur};
    };
}

std::vector<DataCollector::FieldSpec> DataCollector::buildNothingFieldSpecs() const {
    std::vector<FieldSpec> fields = buildGenericFieldSpecs();
    fields.insert(fields.begin(), FieldSpec{"capacity", "/proc/charger/real_soc", DataType::INT, 1, false, &BatteryData::capacity});
    // fields.push_back({"temp_usb_gpio", "/proc/charger/usb_temp_gpio", DataType::INT, 1, false, &BatteryData::temp_usb_gpio});
    fields.push_back({"health", "/proc/charger/battery_health", DataType::INT, 1, false, &BatteryData::health});
    fields.push_back({"charge_full", "/proc/charger/nt_quse", DataType::INT, 1000, false, &BatteryData::charge_full});
    fields.push_back({"charge_design", "/proc/charger/nt_qmax", DataType::INT, 1000, false, &BatteryData::charge_design});
    // fields.push_back({"battery_resistance", "/proc/charger/nt_resistance", DataType::INT, 1, false, &BatteryData::battery_resistance});
    fields.push_back({"wireless_type", "/sys/class/qcom-battery/wireless_type", DataType::STRING, 1, false, &BatteryData::wireless_type});
    fields.push_back({"scenario_fcc", "/proc/charger/scenario_fcc", DataType::INT, 1, true, &BatteryData::scenario_fcc});
    // fields.push_back({"nt_otg_enable", "/proc/charger/nt_otg_enable", DataType::INT, 1, false, &BatteryData::nt_otg_enable});
    fields.push_back({"nt_abnormal_status", "/proc/charger/nt_abnormal_status", DataType::INT, 1, false, &BatteryData::nt_abnormal_status});
    return fields;
}

DataCollector::DeviceProfile DataCollector::detectDeviceProfile(const std::string& fingerprint) const {
    DeviceProfile profile;
    std::string haystack = toLowerCopy(fingerprint);
    if (haystack.find("nothing") != std::string::npos) {
        profile.name = "nothing";
        profile.batteryStatusPath = "/sys/class/power_supply/battery/status";
        profile.scenarioFccPath = "/proc/charger/scenario_fcc";
        profile.fields = buildNothingFieldSpecs();
        return profile;
    }

    profile.name = "generic";
    profile.batteryStatusPath = Config::BATTERY_STATUS_PATH;
    profile.scenarioFccPath = Config::SCENARIO_FCC_PATH;
    profile.fields = buildGenericFieldSpecs();
    return profile;
}

void DataCollector::applyFieldSpec(const FieldSpec& spec, BatteryData& data) {
    if (std::holds_alternative<IntFieldPtr>(spec.target)) {
        IntFieldPtr ptr = std::get<IntFieldPtr>(spec.target);
        int& field = data.*ptr;
        readFile(spec.path, field, DataType::INT);
        if (spec.scaleDivisor > 1 && field != -1 && field != BatteryData::INVALID_VALUE) {
            field /= spec.scaleDivisor;
        }
        return;
    }

    StringFieldPtr ptr = std::get<StringFieldPtr>(spec.target);
    std::string& field = data.*ptr;
    readFile(spec.path, field, DataType::STRING);
}

bool DataCollector::start() {
    if (running) return true;
    running = true;

    std::string fingerprint = readDeviceFingerprint();
    activeProfile = detectDeviceProfile(fingerprint);
    batteryStatusPath = activeProfile.batteryStatusPath;
    scenarioFccPath = activeProfile.scenarioFccPath;
    statusPathUnavailable.store(batteryStatusPath.empty(), std::memory_order_release);
    scenarioPathUnavailable.store(scenarioFccPath.empty(), std::memory_order_release);
    scenarioUnavailableLogged.store(false, std::memory_order_release);
    LOG_INFO("Detected device profile: " + activeProfile.name);
    LOG_INFO("Device fingerprint: " + fingerprint);
    LOG_INFO("Profile file paths: status=" + batteryStatusPath + ", scenario_fcc=" + scenarioFccPath);

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
        if (!scenarioPathUnavailable.load(std::memory_order_acquire)) {
            std::lock_guard<std::mutex> configLock(configMutex);
            applyLimitLocked(currentConfig.targetLimit, "safety rule");
        } else if (!scenarioUnavailableLogged.exchange(true, std::memory_order_acq_rel)) {
            LOG_WARN("scenario_fcc path unavailable, skip safety-rule write");
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

    const std::vector<FieldSpec>* fieldSpecs = &activeProfile.fields;
    std::vector<FieldSpec> fallbackSpecs;
    if (fieldSpecs->empty()) {
        fallbackSpecs = buildGenericFieldSpecs();
        fieldSpecs = &fallbackSpecs;
        LOG_WARN("Active profile field specs empty, falling back to generic specs");
    }

    for (const FieldSpec& spec : *fieldSpecs) {
        applyFieldSpec(spec, data);
    }

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
    if (statusPathUnavailable.load(std::memory_order_acquire)) {
        LOG_WARN("Status monitoring skipped: status path unavailable");
        return true;
    }

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
    statusWatchFd = inotify_add_watch(statusInotifyFd, batteryStatusPath.c_str(), IN_MODIFY);
    if (statusWatchFd < 0) {
        if (errno == ENOENT || errno == ENOTDIR || errno == EACCES || errno == EPERM) {
            LOG_WARN("Status monitoring disabled for unavailable path: " + batteryStatusPath +
                     " (errno=" + std::to_string(errno) + ")");
            statusPathUnavailable.store(true, std::memory_order_release);
            close(statusInotifyFd);
            statusInotifyFd = -1;
            return true;
        }
        LOG_ERROR("Failed to add inotify watch for status monitoring");
        close(statusInotifyFd);
        statusInotifyFd = -1;
        return false;
    }
    
    LOG_INFO("Status monitoring started for " + batteryStatusPath);
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
    if (scenarioPathUnavailable.load(std::memory_order_acquire)) {
        if (!scenarioUnavailableLogged.exchange(true, std::memory_order_acq_rel)) {
            LOG_WARN("Skipping scenario_fcc write: path unavailable");
        }
        return false;
    }
    if (scenarioFccPath.empty()) {
        LOG_ERROR("Cannot write scenario_fcc: profile path is empty");
        scenarioPathUnavailable.store(true, std::memory_order_release);
        return false;
    }
    return writeFile(scenarioFccPath, std::to_string(value));
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

    if (scenarioPathUnavailable.load(std::memory_order_acquire)) {
        LOG_WARN("Scenario monitoring skipped: scenario_fcc path unavailable");
        return true;
    }
    
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
    scenarioWatchFd = inotify_add_watch(scenarioInotifyFd, scenarioFccPath.c_str(), IN_MODIFY);
    if (scenarioWatchFd < 0) {
        if (errno == ENOENT || errno == ENOTDIR || errno == EACCES || errno == EPERM) {
            LOG_WARN("Scenario monitoring disabled for unavailable path: " + scenarioFccPath +
                     " (errno=" + std::to_string(errno) + ")");
            scenarioPathUnavailable.store(true, std::memory_order_release);
            close(scenarioInotifyFd);
            scenarioInotifyFd = -1;
            scenarioMonitoring = false;
            return true;
        }
        LOG_ERROR("Failed to add inotify watch");
        close(scenarioInotifyFd);
        scenarioInotifyFd = -1;
        return false;
    }
    
    scenarioMonitoring = true;
    LOG_INFO("Scenario monitoring started for " + scenarioFccPath);
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
    int current = BatteryData::INVALID_VALUE;
    {
        std::lock_guard<std::mutex> dataLock(dataMutex);
        scenario_fcc = currentData.scenario_fcc;
        current = currentData.current_now;
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
    } else if (isCharging.load() && (current != BatteryData::INVALID_VALUE) && (current > currentConfig.actualLimit)) { //当充电且电流大于预设值，强制写入
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
