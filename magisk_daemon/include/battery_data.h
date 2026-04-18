#ifndef BATTERY_DATA_H
#define BATTERY_DATA_H

#include <string>
#include <unordered_map>
#include <json/json.h>

struct BatteryData {
    static constexpr int INVALID_VALUE = 65536;  // 无效值标记，超出所有合理取值范围

    long timestamp = 0;

    int capacity = -1;              // 0-100, 电池电量百分比
    int voltage_now = -1;           // 当前电池电压(mV)  // 和adc读数不一致？
    int voltage_max = -1;           // 最大电池电压(mV)
    int voltage_ocv = -1;           // 电池开路电压(mV) //最低电压？
    int current_now = INVALID_VALUE;            // 当前电池电流(mA) // 同max？
    int current_avg = INVALID_VALUE;            // 平均电池电流(mA)
    int temp_battery = -1;          // 电池温度(0.1°C)
    // int temp_usb = -1;              // USB温度(0.1°C)
    // int temp_usb_gpio = -1;         // USB GPIO温度(0.1°C) 与usb有差别 略低
    int health = -1;                // 电池健康状态 0-100
    std::string status_str = "";    // 电池状态文本 "Charging", "Discharging", "Full"
    std::string charge_type_str = ""; // 充电类型文本 "Fast", "Standard", "N/A"
    int charge_counter = -1;        // charge counter in mAh  // 剩余电量
    int cycle_count = -1;           // 电池循环次数
    int charge_full = -1;           // 实际充满容量 in mAh
    int charge_design = -1;         // 设计充满容量 in mAh
    // int battery_resistance = -1;    // 电池内阻 in mΩ?
    int usb_online = -1;             // 0 or 1
    int usb_voltage_now = -1;        // voltage in mV
    int usb_voltage_max = -1;        // max voltage in mV
    int in_current_now = INVALID_VALUE;        // current in mA // usb与wls相同数据
    int usb_current_max = INVALID_VALUE;        // max current in mA
    // int usb_input_current_limit = -1; // input current limit in μA 同max // usb与wls相同数据
    std::string usb_type = "";       // [Unknown] SDP DCP CDP ACA C PD PD_DRP PD_PPS BrickID
    int wireless_online = -1;         // 0 or 1
    int wireless_voltage_now = -1;    // voltage in mV
    int wireless_voltage_max = -1;    // max voltage in mV
    int wireless_current_max = INVALID_VALUE;    // max current in mA
    std::string wireless_type = "";       // [Unknown] BPP
    // int wireless_boost_en = -1;       // 无线升压使能 指示反充
    // int wls_tx_volt = -1;            // 无线充电发射电压 in μV
    // int wls_tx_curr = -1;            // 无线充电发射电流 in μA
    // int wls_rev_status = -1;         // 反向充电状态
    // int wls_rev_fod = -1;            // 反向异物检测
    // int restrict_chg = -1;           // 限制充电使能
    // int restrict_cur = -1;           // 限制充电电流(μA)
    int scenario_fcc = -1;           // 场景快速充电电流(mA)
    // int nt_otg_enable = -1;          // OTG使能
    int nt_abnormal_status = -1;     // 异常状态

    BatteryData() = default;

    Json::Value toJson(const std::unordered_map<std::string, bool>& readableFields) const {
        auto shouldInclude = [&readableFields](const char* key) {
            auto it = readableFields.find(key);
            if (it == readableFields.end()) {
                return true;
            }
            return it->second;
        };

        Json::Value json;
        json["version"] = Json::Value::Int(1);
        json["timestamp"] = Json::Value::Int64(timestamp);

        if (shouldInclude("capacity")) json["capacity"] = Json::Value::Int(capacity);
        if (shouldInclude("voltage_now")) json["voltage_now"] = Json::Value::Int(voltage_now);
        if (shouldInclude("voltage_max")) json["voltage_max"] = Json::Value::Int(voltage_max);
        if (shouldInclude("voltage_ocv")) json["voltage_ocv"] = Json::Value::Int(voltage_ocv);
        if (shouldInclude("current_now")) json["current_now"] = Json::Value::Int(current_now);
        if (shouldInclude("current_avg")) json["current_avg"] = Json::Value::Int(current_avg);
        if (shouldInclude("temp_battery")) json["temp_battery"] = Json::Value::Int(temp_battery);
        // json["temp_usb"] = Json::Value::Int(temp_usb);
        // json["temp_usb_gpio"] = Json::Value::Int(temp_usb_gpio);
        if (shouldInclude("health")) json["health"] = Json::Value::Int(health);
        if (shouldInclude("status_str")) json["status_str"] = status_str;
        if (shouldInclude("charge_type_str")) json["charge_type_str"] = charge_type_str;
        if (shouldInclude("charge_counter")) json["charge_counter"] = Json::Value::Int(charge_counter);
        if (shouldInclude("cycle_count")) json["cycle_count"] = Json::Value::Int(cycle_count);
        if (shouldInclude("charge_full")) json["charge_full"] = Json::Value::Int(charge_full);
        if (shouldInclude("charge_design")) json["charge_design"] = Json::Value::Int(charge_design);
        // json["battery_resistance"] = Json::Value::Int(battery_resistance);
        if (shouldInclude("usb_online")) json["usb_online"] = Json::Value::Int(usb_online);
        if (shouldInclude("usb_voltage_now")) json["usb_voltage_now"] = Json::Value::Int(usb_voltage_now);
        if (shouldInclude("usb_voltage_max")) json["usb_voltage_max"] = Json::Value::Int(usb_voltage_max);
        if (shouldInclude("in_current_now")) json["in_current_now"] = Json::Value::Int(in_current_now);
        if (shouldInclude("usb_current_max")) json["usb_current_max"] = Json::Value::Int(usb_current_max);
        // json["usb_input_current_limit"] = Json::Value::Int(usb_input_current_limit);
        if (shouldInclude("usb_type")) json["usb_type"] = usb_type;
        if (shouldInclude("wireless_online")) json["wireless_online"] = Json::Value::Int(wireless_online);
        if (shouldInclude("wireless_voltage_now")) json["wireless_voltage_now"] = Json::Value::Int(wireless_voltage_now);
        if (shouldInclude("wireless_voltage_max")) json["wireless_voltage_max"] = Json::Value::Int(wireless_voltage_max);
        if (shouldInclude("wireless_current_max")) json["wireless_current_max"] = Json::Value::Int(wireless_current_max);
        if (shouldInclude("wireless_type")) json["wireless_type"] = wireless_type;
        // json["wireless_boost_en"] = Json::Value::Int(wireless_boost_en);
        // json["wls_tx_volt"] = Json::Value::Int(wls_tx_volt);
        // json["wls_tx_curr"] = Json::Value::Int(wls_tx_curr);
        // json["wls_rev_status"] = Json::Value::Int(wls_rev_status);
        // json["wls_rev_fod"] = Json::Value::Int(wls_rev_fod);
        // json["restrict_chg"] = Json::Value::Int(restrict_chg);
        // json["restrict_cur"] = Json::Value::Int(restrict_cur);
        if (shouldInclude("scenario_fcc")) json["scenario_fcc"] = Json::Value::Int(scenario_fcc);
        // json["nt_otg_enable"] = Json::Value::Int(nt_otg_enable);
        if (shouldInclude("nt_abnormal_status")) json["nt_abnormal_status"] = Json::Value::Int(nt_abnormal_status);
        return json;
    }

    Json::Value toJson() const {
        static const std::unordered_map<std::string, bool> kIncludeAllFields;
        return toJson(kIncludeAllFields);
    }
    
    bool operator==(const BatteryData& other) const {
        return timestamp == other.timestamp;  // 只比较时间戳
    }
    
    bool operator!=(const BatteryData& other) const {
        return !(*this == other);
    }
};

#endif // BATTERY_DATA_H
