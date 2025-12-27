#ifndef BATTERY_DATA_H
#define BATTERY_DATA_H

#include <string>
#include <json/json.h>

struct BatteryData {
    long timestamp;
    
    // Battery fields
    int capacity;              // 0-100, percentage
    int temp;                  // temperature in 0.1°C
    int voltage_now;            // voltage in μV
    int current_now;            // current in μA
    std::string status;         // "Charging", "Discharging", "Full", "Not charging"
    std::string health;         // "Good", "Overheat", "Dead", etc.
    int charge_counter;         // charge counter in μAh
    int charge_full;            // full charge in μAh
    int charge_full_design;     // design full charge in μAh
    int cycle_count;            // battery cycle count
    int time_to_empty_avg;      // time to empty in seconds
    int time_to_full_avg;       // time to full in seconds
    int time_to_full_now;       // time to full now in seconds
    int charge_control_start_threshold;  // start threshold 0-100
    int charge_control_end_threshold;    // end threshold 0-100
    int charge_control_limit;    // charge limit in μA
    int charge_control_limit_max; // max charge limit in μA
    std::string technology;      // "Li-ion", "Li-poly", etc.
    std::string model_name;      // battery model name
    std::string charge_type;     // "Fast", "Trickle", "None"
    int present;                // 0 or 1
    
    // USB fields
    int usb_online;             // 0 or 1
    int usb_voltage_now;        // voltage in μV
    int usb_voltage_max;        // max voltage in μV
    int usb_current_now;        // current in μA
    int usb_current_max;        // max current in μA
    int usb_input_current_limit; // input current limit in μA
    int usb_temp;               // temperature in 0.1°C
    std::string usb_type;       // "SDP", "DCP", "CDP", "PD", etc.
    
    // Wireless fields
    int wireless_online;         // 0 or 1
    int wireless_voltage_now;    // voltage in μV
    int wireless_voltage_max;    // max voltage in μV
    int wireless_current_now;    // current in μA
    int wireless_current_max;    // max current in μA
    int wireless_input_current_limit; // input current limit in μA
    int wireless_temp;          // temperature in 0.1°C
    
    BatteryData() : timestamp(0), capacity(-1), temp(-1), voltage_now(-1), current_now(-1),
                    charge_counter(-1), charge_full(-1), charge_full_design(-1), cycle_count(-1),
                    time_to_empty_avg(-1), time_to_full_avg(-1), time_to_full_now(-1),
                    charge_control_start_threshold(-1), charge_control_end_threshold(-1),
                    charge_control_limit(-1), charge_control_limit_max(-1), present(-1),
                    usb_online(-1), usb_voltage_now(-1), usb_voltage_max(-1), usb_current_now(-1),
                    usb_current_max(-1), usb_input_current_limit(-1), usb_temp(-1),
                    wireless_online(-1), wireless_voltage_now(-1), wireless_voltage_max(-1),
                    wireless_current_now(-1), wireless_current_max(-1), wireless_input_current_limit(-1),
                    wireless_temp(-1) {}
    
    Json::Value toJson() const {
        Json::Value json;
        json["timestamp"] = Json::Value::Int64(timestamp);
        
        // Battery data
        json["capacity"] = Json::Value::Int(capacity);
        json["temp"] = Json::Value::Int(temp);
        json["voltage_now"] = Json::Value::Int(voltage_now);
        json["current_now"] = Json::Value::Int(current_now);
        json["status"] = status;
        json["health"] = health;
        json["charge_counter"] = Json::Value::Int(charge_counter);
        json["charge_full"] = Json::Value::Int(charge_full);
        json["charge_full_design"] = Json::Value::Int(charge_full_design);
        json["cycle_count"] = Json::Value::Int(cycle_count);
        json["time_to_empty_avg"] = Json::Value::Int(time_to_empty_avg);
        json["time_to_full_avg"] = Json::Value::Int(time_to_full_avg);
        json["time_to_full_now"] = Json::Value::Int(time_to_full_now);
        json["charge_control_start_threshold"] = Json::Value::Int(charge_control_start_threshold);
        json["charge_control_end_threshold"] = Json::Value::Int(charge_control_end_threshold);
        json["charge_control_limit"] = Json::Value::Int(charge_control_limit);
        json["charge_control_limit_max"] = Json::Value::Int(charge_control_limit_max);
        json["technology"] = technology;
        json["model_name"] = model_name;
        json["charge_type"] = charge_type;
        json["present"] = Json::Value::Int(present);
        
        // USB data
        json["usb_online"] = Json::Value::Int(usb_online);
        json["usb_voltage_now"] = Json::Value::Int(usb_voltage_now);
        json["usb_voltage_max"] = Json::Value::Int(usb_voltage_max);
        json["usb_current_now"] = Json::Value::Int(usb_current_now);
        json["usb_current_max"] = Json::Value::Int(usb_current_max);
        json["usb_input_current_limit"] = Json::Value::Int(usb_input_current_limit);
        json["usb_temp"] = Json::Value::Int(usb_temp);
        json["usb_type"] = usb_type;
        
        // Wireless data
        json["wireless_online"] = Json::Value::Int(wireless_online);
        json["wireless_voltage_now"] = Json::Value::Int(wireless_voltage_now);
        json["wireless_voltage_max"] = Json::Value::Int(wireless_voltage_max);
        json["wireless_current_now"] = Json::Value::Int(wireless_current_now);
        json["wireless_current_max"] = Json::Value::Int(wireless_current_max);
        json["wireless_input_current_limit"] = Json::Value::Int(wireless_input_current_limit);
        json["wireless_temp"] = Json::Value::Int(wireless_temp);

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
