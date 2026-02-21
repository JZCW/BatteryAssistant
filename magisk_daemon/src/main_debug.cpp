#include <iostream>
#include <signal.h>
#include <unistd.h>
#include "data_collector.h"
#include "cache_manager.h"
#include "battery_data.h"

volatile sig_atomic_t running = 1;

void signalHandler(int signal) {
    running = 0;
}

void printBatteryData(const BatteryData& data) {
    std::cout << data.timestamp << ","
              << data.capacity << ","
              << data.voltage_now << ","
              << data.voltage_max << ","
              << data.voltage_ocv << ","
              << data.current_now << ","
              << data.current_avg << ","
              << data.temp_battery << ","
              // << data.temp_usb << ","
              // << data.temp_usb_gpio << ","
              << data.health << ","
              << data.status_str << ","
              << data.charge_type_str << ","
              << data.charge_counter << ","
              << data.cycle_count << ","
              << data.charge_full << ","
              << data.charge_design << ","
            //   << data.battery_resistance << ","
              << data.usb_online << ","
              << data.usb_voltage_now << ","
              << data.usb_voltage_max << ","
              << data.in_current_now << ","
              << data.usb_current_max << ","
              // << data.usb_input_current_limit << ","
              << data.usb_type << ","
              << data.wireless_online << ","
              << data.wireless_voltage_now << ","
              << data.wireless_voltage_max << ","
              << data.wireless_current_max << ","
              << data.wireless_type << ","
            //   << data.wireless_boost_en << ","
            //   << data.wls_tx_volt << ","
            //   << data.wls_tx_curr << ","
            //   << data.wls_rev_status << ","
            //   << data.wls_rev_fod << ","
            //   << data.restrict_chg << ","
            //   << data.restrict_cur << ","
              << data.scenario_fcc << ","
            //   << data.nt_otg_enable << ","
              << data.nt_abnormal_status << std::endl;
}

int main() {
    signal(SIGINT, signalHandler);
    signal(SIGTERM, signalHandler);
    
    std::cout << "========================================" << std::endl;
    std::cout << "Battery Service Daemon - DEBUG VERSION" << std::endl;
    std::cout << "========================================" << std::endl;
    std::cout << "Press Ctrl+C to stop" << std::endl;
    std::cout << "========================================" << std::endl;
    std::cout << std::endl;
    
    try {
        // 初始化组件
        CacheManager::getInstance();
        
        // 启动数据采集器
        DataCollector& dataCollector = DataCollector::getInstance();
        dataCollector.start();
        
        // 模拟客户端连接，使采集器以1秒间隔采集数据
        dataCollector.onClientConnected();
        
        std::cout << "Data collector started with 1s interval" << std::endl;
        std::cout << std::endl;
        
        // 主循环 - 每秒打印一次数据
        while (running) {
            sleep(2);
            
            // 从缓存获取最新数据
            BatteryData data = CacheManager::getInstance().getBatteryData(false); // 不标记为已读
            
            printBatteryData(data);
        }
        
        // 清理
        std::cout << "Stopping data collector..." << std::endl;
        dataCollector.stop();
        
        std::cout << "Battery Service Daemon stopped" << std::endl;
        
    } catch (const std::exception& e) {
        std::cerr << "Exception: " << e.what() << std::endl;
        return 1;
    }
    
    return 0;
}
