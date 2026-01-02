#include "data_collector.h"
#include "cache_manager.h"
#include "logger.h"
#include <fstream>
#include <filesystem>
#include <thread>
#include <chrono>
#include <type_traits>

DataCollector::DataCollector() {}

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
    readFile("/proc/charger/real_soc", data.capacity, DataType::INT);
    readFile("/sys/class/power_supply/battery/voltage_now", data.voltage_now, DataType::INT);
    readFile("/sys/class/power_supply/battery/voltage_max", data.voltage_max, DataType::INT);
    readFile("/sys/class/power_supply/battery/voltage_ocv", data.voltage_ocv, DataType::INT);
    readFile("/sys/class/qcom-battery/terminate_voltage", data.voltage_trm, DataType::INT);
    readFile("/sys/class/power_supply/battery/current_now", data.current_now, DataType::INT);
    readFile("/sys/class/power_supply/battery/current_avg", data.current_avg, DataType::INT);
    readFile("/sys/class/power_supply/battery/power_now", data.power_now, DataType::INT);
    readFile("/sys/class/power_supply/battery/power_avg", data.power_avg, DataType::INT);
    readFile("/sys/class/qcom-battery/charge_power", data.power_charge, DataType::INT);
    readFile("/sys/class/power_supply/battery/temp", data.temp_battery, DataType::INT);
    readFile("/sys/class/power_supply/usb/temp", data.temp_usb, DataType::INT);
    readFile("/proc/charger/usb_temp_gpio", data.temp_usb_gpio, DataType::INT);
    readFile("/sys/class/power_supply/wireless/temp", data.temp_wls, DataType::INT);
    readFile("/proc/charger/battery_health", data.health, DataType::INT);
    readFile("/sys/class/power_supply/battery/health", data.health_str, DataType::STRING);
    readFile("/sys/class/power_supply/battery/technology", data.battery_type, DataType::STRING);
    readFile("/sys/class/power_supply/battery/model_name", data.battery_model, DataType::STRING);
    readFile("/proc/charger/bat_manufacture_date", data.battery_manu_date, DataType::STRING);
    readFile("/sys/class/qcom-battery/battery_parallel_cell_count", data.battery_parallel_num, DataType::STRING);
    readFile("/sys/class/power_supply/battery/status", data.status_str, DataType::STRING);
    readFile("/sys/class/power_supply/battery/charge_type", data.charge_type_str, DataType::STRING);
    readFile("/sys/class/qcom-battery/chemical_id", data.battery_type_id, DataType::INT);
    readFile("/sys/class/power_supply/battery/charge_counter", data.charge_counter, DataType::INT);
    readFile("/sys/class/power_supply/battery/cycle_count", data.cycle_count, DataType::INT);
    readFile("/proc/charger/nt_quse", data.charge_full, DataType::INT);
    readFile("/proc/charger/nt_qmax", data.charge_design, DataType::INT);
    readFile("/proc/charger/nt_resistance", data.battery_resistance, DataType::INT);
    readFile("/sys/class/power_supply/battery/time_to_full_now", data.time_to_full_now, DataType::INT);
    readFile("/sys/class/power_supply/battery/time_to_full_avg", data.time_to_full_avg, DataType::INT);
    readFile("/sys/class/power_supply/battery/time_to_empty_avg", data.time_to_empty_avg, DataType::INT);
    readFile("/sys/class/power_supply/usb/online", data.usb_online, DataType::INT);
    readFile("/sys/class/power_supply/usb/voltage_now", data.usb_voltage_now, DataType::INT);
    readFile("/sys/class/power_supply/usb/voltage_max", data.usb_voltage_max, DataType::INT);
    readFile("/sys/class/power_supply/usb/current_now", data.usb_current_now, DataType::INT);
    readFile("/sys/class/power_supply/usb/current_max", data.usb_current_max, DataType::INT);
    readFile("/sys/class/power_supply/usb/input_current_limit", data.usb_input_current_limit, DataType::INT);
    readFile("/sys/class/power_supply/usb/usb_type", data.usb_type, DataType::STRING);
    readFile("/sys/class/power_supply/wireless/online", data.wireless_online, DataType::INT);
    readFile("/sys/class/power_supply/wireless/voltage_now", data.wireless_voltage_now, DataType::INT);
    readFile("/sys/class/power_supply/wireless/voltage_max", data.wireless_voltage_max, DataType::INT);
    readFile("/sys/class/power_supply/wireless/current_now", data.wireless_current_now, DataType::INT);
    readFile("/sys/class/power_supply/wireless/current_max", data.wireless_current_max, DataType::INT);
    readFile("/sys/class/power_supply/wireless/input_current_limit", data.wireless_input_current_limit, DataType::INT);
    readFile("/sys/class/qcom-battery/wireless_type", data.wireless_type, DataType::STRING);
    readFile("/sys/class/qcom-battery/wireless_boost_en", data.wireless_boost_en, DataType::INT);
    readFile("/sys/class/qcom-battery/wls_volt_tx", data.wls_tx_volt, DataType::INT);
    readFile("/sys/class/qcom-battery/wls_curr_tx", data.wls_tx_curr, DataType::INT);
    readFile("/sys/class/qcom-battery/wls_reverse_status", data.wls_rev_status, DataType::INT);
    readFile("/sys/class/qcom-battery/wls_reverse_fod", data.wls_rev_fod, DataType::INT);
    readFile("/sys/class/qcom-battery/wls_en", data.wls_en, DataType::INT);
    readFile("/sys/class/qcom-battery/restrict_chg", data.restrict_chg, DataType::INT);
    readFile("/sys/class/qcom-battery/restrict_cur", data.restrict_cur, DataType::INT);
    readFile("/proc/charger/scenario_fcc", data.scenario_fcc, DataType::INT);
    readFile("/proc/charger/charge_pump_enable", data.charge_pump_enable, DataType::INT);
    readFile("/proc/charger/charge_exist_pump", data.charge_exist_pump, DataType::INT);
    readFile("/proc/charger/charge_exist_buck", data.charge_exist_buck, DataType::INT);
    readFile("/proc/charger/charge_buck_enable", data.charge_buck_enable, DataType::INT);
    readFile("/proc/charger/charge_exist_wls", data.charge_exist_wls, DataType::INT);
    readFile("/proc/charger/nt_otg_enable", data.nt_otg_enable, DataType::INT);
    readFile("/sys/class/qcom-battery/flash_active", data.flash_active, DataType::INT);
    readFile("/proc/charger/chg_data_id", data.chg_data_id, DataType::INT);
    readFile("/proc/charger/nt_abnormal_status", data.nt_abnormal_status, DataType::INT);
    readFile("/proc/charger/ibus_now", data.ibus_now, DataType::INT);
    readFile("/proc/charger/IbusMmax_mA", data.ibus_max, DataType::INT);
    readFile("/proc/charger/maxchargervoltage", data.charge_max_volt, DataType::INT);
    readFile("/proc/charger/maxchargercurrent", data.charge_max_curr, DataType::INT);
    readFile("/proc/charger/nt_adp_power", data.charge_max_power, DataType::INT);

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