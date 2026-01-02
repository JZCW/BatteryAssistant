#ifndef BATTERY_DATA_H
#define BATTERY_DATA_H

#include <string>
#include <json/json.h>

struct BatteryData {
    long timestamp = 0;
    
    int capacity = -1;              // 0-10000, 电池电量百分比*100
    int voltage_now = -1;           // 当前电池电压(μV)  // 和adc读数不一致？
    int voltage_max = -1;           // 最大电池电压(μV)
    int voltage_ocv = -1;           // 电池开路电压(μV)
    int voltage_trm = -1;           // 充电终止电压
    int current_now = -1;           // 当前电池电流(μA) // 同max？
    int current_avg = -1;           // 平均电池电流(μA)
    int power_now = -1;             // 当前电池功率(μW) // 读数很奇怪
    int power_avg = -1;             // 平均电池功率(μW)
    int power_charge = -1;          // 充电功率(μW)
    int temp_battery = -1;          // 电池温度(0.1°C)
    int temp_usb = -1;              // USB温度(0.1°C)
    int temp_usb_gpio = -1;         // USB GPIO温度(0.1°C)
    int temp_wls = -1;              // 无线充电温度(0.1°C)
    int health = -1;                // 电池健康状态 0-100
    std::string health_str = "";    // 电池健康状态文本 "Good", "Overheat", "Dead", etc.
    std::string battery_type = "";  // 电池类型
    int battery_type_id = -1;       // 电池类型ID ?
    std::string battery_model = ""; // 电池型号
    std::string battery_manu_date = ""; // 电池生产日期
    std::string battery_parallel_num = ""; // 电池并联数
    std::string status_str = "";    // 电池状态文本 "Charging", "Discharging", "Full", "Not charging"
    std::string charge_type_str = ""; // 充电类型文本 "Fast", "Trickle", "None"
    int charge_counter = -1;        // charge counter in μAh  // 意义不明 real_cap?
    int cycle_count = -1;           // 电池循环次数
    int charge_full = -1;           // 实际充满容量 in μAh
    int charge_design = -1;         // 设计充满容量 in μAh
    int battery_resistance = -1;    // 电池内阻 in mΩ?
    int time_to_full_now = -1;       // time to full now in seconds
    int time_to_full_avg = -1;       // time to full in seconds
    int time_to_empty_avg = -1;      // time to empty in seconds
    int usb_online = -1;             // 0 or 1
    int usb_voltage_now = -1;        // voltage in μV
    int usb_voltage_max = -1;        // max voltage in μV
    int usb_current_now = -1;        // current in μA
    int usb_current_max = -1;        // max current in μA
    int usb_input_current_limit = -1; // input current limit in μA
    std::string usb_type = "";       // [Unknown] SDP DCP CDP ACA C PD PD_DRP PD_PPS BrickID
    int wireless_online = -1;         // 0 or 1
    int wireless_voltage_now = -1;    // voltage in μV
    int wireless_voltage_max = -1;    // max voltage in μV
    int wireless_current_now = -1;    // current in μA
    int wireless_current_max = -1;    // max current in μA
    int wireless_input_current_limit = -1; // input current limit in μA
    std::string wireless_type = "";       // [Unknown] 
    int wireless_boost_en = -1;       // 无线升压使能
    int wls_tx_volt = -1;            // 无线充电发射电压 in μV
    int wls_tx_curr = -1;            // 无线充电发射电流 in μA
    int wls_rev_status = -1;         // 反向充电状态
    int wls_rev_fod = -1;            // 反向异物检测
    int wls_en = -1;                 // 无线充电使能
    int restrict_chg = -1;           // 限制充电使能
    int restrict_cur = -1;           // 限制充电电流(μA)
    int scenario_fcc = -1;           // 场景快速充电电流(μA)
    int charge_pump_enable = -1;     // 充电泵使能
    int charge_exist_pump = -1;      // 充电泵存在
    int charge_exist_buck = -1;      // 是否存在Buck转换器
    int charge_buck_enable = -1;     // Buck转换器使能
    int charge_exist_wls = -1;       // 是否存在无线充电
    int nt_otg_enable = -1;          // OTG使能
    int flash_active = -1;           // 闪充激活
    int chg_data_id = -1;            // 充电数据ID
    int nt_abnormal_status = -1;     // 异常状态
    int ibus_now = -1;               // ibus电流 in μA
    int ibus_max = -1;               // ibus最大电流 in μA
    int charge_max_volt = -1;        // 充电最大电压 in μV
    int charge_max_curr = -1;        // 充电最大电流 in μA
    int charge_max_power = -1;       // 充电最大功率 in μW

    BatteryData() = default;
    
    Json::Value toJson() const {
        Json::Value json;
        json["timestamp"] = Json::Value::Int64(timestamp);
        json["capacity"] = Json::Value::Int(capacity);
        json["voltage_now"] = Json::Value::Int(voltage_now);
        json["voltage_max"] = Json::Value::Int(voltage_max);
        json["voltage_ocv"] = Json::Value::Int(voltage_ocv);
        json["voltage_trm"] = Json::Value::Int(voltage_trm);
        json["current_now"] = Json::Value::Int(current_now);
        json["current_avg"] = Json::Value::Int(current_avg);
        json["power_now"] = Json::Value::Int(power_now);
        json["power_avg"] = Json::Value::Int(power_avg);
        json["power_charge"] = Json::Value::Int(power_charge);
        json["temp_battery"] = Json::Value::Int(temp_battery);
        json["temp_usb"] = Json::Value::Int(temp_usb);
        json["temp_usb_gpio"] = Json::Value::Int(temp_usb_gpio);
        json["temp_wls"] = Json::Value::Int(temp_wls);
        json["health"] = Json::Value::Int(health);
        json["health_str"] = health_str;
        json["battery_type"] = battery_type;
        json["battery_type_id"] = Json::Value::Int(battery_type_id);
        json["battery_model"] = battery_model;
        json["battery_manu_date"] = battery_manu_date;
        json["battery_parallel_num"] = battery_parallel_num;
        json["status_str"] = status_str;
        json["charge_type_str"] = charge_type_str;
        json["charge_counter"] = Json::Value::Int(charge_counter);
        json["cycle_count"] = Json::Value::Int(cycle_count);
        json["charge_full"] = Json::Value::Int(charge_full);
        json["charge_design"] = Json::Value::Int(charge_design);
        json["battery_resistance"] = Json::Value::Int(battery_resistance);
        json["time_to_full_now"] = Json::Value::Int(time_to_full_now);
        json["time_to_full_avg"] = Json::Value::Int(time_to_full_avg);
        json["time_to_empty_avg"] = Json::Value::Int(time_to_empty_avg);
        json["usb_online"] = Json::Value::Int(usb_online);
        json["usb_voltage_now"] = Json::Value::Int(usb_voltage_now);
        json["usb_voltage_max"] = Json::Value::Int(usb_voltage_max);
        json["usb_current_now"] = Json::Value::Int(usb_current_now);
        json["usb_current_max"] = Json::Value::Int(usb_current_max);
        json["usb_input_current_limit"] = Json::Value::Int(usb_input_current_limit);
        json["usb_type"] = usb_type;
        json["wireless_online"] = Json::Value::Int(wireless_online);
        json["wireless_voltage_now"] = Json::Value::Int(wireless_voltage_now);
        json["wireless_voltage_max"] = Json::Value::Int(wireless_voltage_max);
        json["wireless_current_now"] = Json::Value::Int(wireless_current_now);
        json["wireless_current_max"] = Json::Value::Int(wireless_current_max);
        json["wireless_input_current_limit"] = Json::Value::Int(wireless_input_current_limit);
        json["wireless_type"] = wireless_type;
        json["wireless_boost_en"] = Json::Value::Int(wireless_boost_en);
        json["wls_tx_volt"] = Json::Value::Int(wls_tx_volt);
        json["wls_tx_curr"] = Json::Value::Int(wls_tx_curr);
        json["wls_rev_status"] = Json::Value::Int(wls_rev_status);
        json["wls_rev_fod"] = Json::Value::Int(wls_rev_fod);
        json["wls_en"] = Json::Value::Int(wls_en);
        json["restrict_chg"] = Json::Value::Int(restrict_chg);
        json["restrict_cur"] = Json::Value::Int(restrict_cur);
        json["scenario_fcc"] = Json::Value::Int(scenario_fcc);
        json["charge_pump_enable"] = Json::Value::Int(charge_pump_enable);
        json["charge_exist_pump"] = Json::Value::Int(charge_exist_pump);
        json["charge_exist_buck"] = Json::Value::Int(charge_exist_buck);
        json["charge_buck_enable"] = Json::Value::Int(charge_buck_enable);
        json["charge_exist_wls"] = Json::Value::Int(charge_exist_wls);
        json["nt_otg_enable"] = Json::Value::Int(nt_otg_enable);
        json["flash_active"] = Json::Value::Int(flash_active);
        json["chg_data_id"] = Json::Value::Int(chg_data_id);
        json["nt_abnormal_status"] = Json::Value::Int(nt_abnormal_status);
        json["ibus_now"] = Json::Value::Int(ibus_now);
        json["ibus_max"] = Json::Value::Int(ibus_max);
        json["charge_max_volt"] = Json::Value::Int(charge_max_volt);
        json["charge_max_curr"] = Json::Value::Int(charge_max_curr);
        json["charge_max_power"] = Json::Value::Int(charge_max_power);
        return json;
    }
    
    bool operator==(const BatteryData& other) const {
        return timestamp == other.timestamp;  // 只比较时间戳
    }
    
    bool operator!=(const BatteryData& other) const {
        return !(*this == other);
    }
};

#endif // BATTERY_DATA_H
