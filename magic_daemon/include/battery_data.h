#ifndef BATTERY_DATA_H
#define BATTERY_DATA_H

#include <string>
#include <map>
#include <json/json.h>

struct BatteryData {
    long timestamp;
    std::map<std::string, std::string> battery;
    std::map<std::string, std::string> usb;
    std::map<std::string, std::string> wireless;
    
    BatteryData() : timestamp(0) {}
    
    Json::Value toJson() const {
        Json::Value root;
        root["timestamp"] = timestamp;
        
        // Battery data
        Json::Value batteryObj(Json::objectValue);
        for (const auto& pair : battery) {
            batteryObj[pair.first] = pair.second;
        }
        root["battery"] = batteryObj;
        
        // USB data
        Json::Value usbObj(Json::objectValue);
        for (const auto& pair : usb) {
            usbObj[pair.first] = pair.second;
        }
        root["usb"] = usbObj;
        
        // Wireless data
        Json::Value wirelessObj(Json::objectValue);
        for (const auto& pair : wireless) {
            wirelessObj[pair.first] = pair.second;
        }
        root["wireless"] = wirelessObj;
        
        return root;
    }
    
    bool operator==(const BatteryData& other) const {
        return timestamp == other.timestamp &&
               battery == other.battery &&
               usb == other.usb &&
               wireless == other.wireless;
    }
    
    bool operator!=(const BatteryData& other) const {
        return !(*this == other);
    }
};

#endif // BATTERY_DATA_H