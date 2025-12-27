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
        Json::Value root;
        root["timestamp"] = Json::Value::Int64(timestamp);
        
        // Battery data
        Json::Value batteryObj(Json::objectValue);
        if (capacity >= 0) batteryObj["capacity"] = capacity;
        if (temp >= 0) batteryObj["temp"] = temp;
        if (voltage_now >= 0) batteryObj["voltage_now"] = voltage_now;
        if (current_now >= 0) batteryObj["current_now"] = current_now;
        if (!status.empty()) batteryObj["status"] = status;
        if (!health.empty()) batteryObj["health"] = health;
        if (charge_counter >= 0) batteryObj["charge_counter"] = charge_counter;
        if (charge_full >= 0) batteryObj["charge_full"] = charge_full;
        if (charge_full_design >= 0) batteryObj["charge_full_design"] = charge_full_design;
        if (cycle_count >= 0) batteryObj["cycle_count"] = cycle_count;
        if (time_to_empty_avg >= 0) batteryObj["time_to_empty_avg"] = time_to_empty_avg;
        if (time_to_full_avg >= 0) batteryObj["time_to_full_avg"] = time_to_full_avg;
        if (time_to_full_now >= 0) batteryObj["time_to_full_now"] = time_to_full_now;
        if (charge_control_start_threshold >= 0) batteryObj["charge_control_start_threshold"] = charge_control_start_threshold;
        if (charge_control_end_threshold >= 0) batteryObj["charge_control_end_threshold"] = charge_control_end_threshold;
        if (charge_control_limit >= 0) batteryObj["charge_control_limit"] = charge_control_limit;
        if (charge_control_limit_max >= 0) batteryObj["charge_control_limit_max"] = charge_control_limit_max;
        if (!technology.empty()) batteryObj["technology"] = technology;
        if (!model_name.empty()) batteryObj["model_name"] = model_name;
        if (!charge_type.empty()) batteryObj["charge_type"] = charge_type;
        if (present >= 0) batteryObj["present"] = present;
        root["battery"] = batteryObj;
        
        // USB data
        Json::Value usbObj(Json::objectValue);
        if (usb_online >= 0) usbObj["online"] = usb_online;
        if (usb_voltage_now >= 0) usbObj["voltage_now"] = usb_voltage_now;
        if (usb_voltage_max >= 0) usbObj["voltage_max"] = usb_voltage_max;
        if (usb_current_now >= 0) usbObj["current_now"] = usb_current_now;
        if (usb_current_max >= 0) usbObj["current_max"] = usb_current_max;
        if (usb_input_current_limit >= 0) usbObj["input_current_limit"] = usb_input_current_limit;
        if (usb_temp >= 0) usbObj["temp"] = usb_temp;
        if (!usb_type.empty()) usbObj["usb_type"] = usb_type;
        root["usb"] = usbObj;
        
        // Wireless data
        Json::Value wirelessObj(Json::objectValue);
        if (wireless_online >= 0) wirelessObj["online"] = wireless_online;
        if (wireless_voltage_now >= 0) wirelessObj["voltage_now"] = wireless_voltage_now;
        if (wireless_voltage_max >= 0) wirelessObj["voltage_max"] = wireless_voltage_max;
        if (wireless_current_now >= 0) wirelessObj["current_now"] = wireless_current_now;
        if (wireless_current_max >= 0) wirelessObj["current_max"] = wireless_current_max;
        if (wireless_input_current_limit >= 0) wirelessObj["input_current_limit"] = wireless_input_current_limit;
        if (wireless_temp >= 0) wirelessObj["temp"] = wireless_temp;
        root["wireless"] = wirelessObj;
        
        return root;
    }
    
    bool operator==(const BatteryData& other) const {
        return timestamp == other.timestamp &&
               capacity == other.capacity &&
               temp == other.temp &&
               voltage_now == other.voltage_now &&
               current_now == other.current_now &&
               status == other.status &&
               health == other.health &&
               charge_counter == other.charge_counter &&
               charge_full == other.charge_full &&
               charge_full_design == other.charge_full_design &&
               cycle_count == other.cycle_count &&
               time_to_empty_avg == other.time_to_empty_avg &&
               time_to_full_avg == other.time_to_full_avg &&
               time_to_full_now == other.time_to_full_now &&
               charge_control_start_threshold == other.charge_control_start_threshold &&
               charge_control_end_threshold == other.charge_control_end_threshold &&
               charge_control_limit == other.charge_control_limit &&
               charge_control_limit_max == other.charge_control_limit_max &&
               technology == other.technology &&
               model_name == other.model_name &&
               charge_type == other.charge_type &&
               present == other.present &&
               usb_online == other.usb_online &&
               usb_voltage_now == other.usb_voltage_now &&
               usb_voltage_max == other.usb_voltage_max &&
               usb_current_now == other.usb_current_now &&
               usb_current_max == other.usb_current_max &&
               usb_input_current_limit == other.usb_input_current_limit &&
               usb_temp == other.usb_temp &&
               usb_type == other.usb_type &&
               wireless_online == other.wireless_online &&
               wireless_voltage_now == other.wireless_voltage_now &&
               wireless_voltage_max == other.wireless_voltage_max &&
               wireless_current_now == other.wireless_current_now &&
               wireless_current_max == other.wireless_current_max &&
               wireless_input_current_limit == other.wireless_input_current_limit &&
               wireless_temp == other.wireless_temp;
    }
    
    bool operator!=(const BatteryData& other) const {
        return !(*this == other);
    }
};

#endif // BATTERY_DATA_H
