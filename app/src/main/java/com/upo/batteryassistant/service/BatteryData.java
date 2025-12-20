package com.upo.batteryassistant.service;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 电池数据模型
 * 从Magic Daemon获取的电池状态数据
 */
public class BatteryData {
    public long timestamp;
    public Map<String, String> battery = new HashMap<>();
    public Map<String, String> usb = new HashMap<>();
    public Map<String, String> wireless = new HashMap<>();
    
    public BatteryData() {
        // 默认构造函数
    }
    
    public static BatteryData fromJson(JSONObject json) throws JSONException {
        BatteryData data = new BatteryData();
        
        data.timestamp = json.getLong("timestamp");
        
        // 解析电池数据
        if (json.has("battery")) {
            JSONObject battery = json.getJSONObject("battery");
            Iterator<String> keys = battery.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                data.battery.put(key, battery.getString(key));
            }
        }
        
        // 解析USB数据
        if (json.has("usb")) {
            JSONObject usb = json.getJSONObject("usb");
            Iterator<String> keys = usb.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                data.usb.put(key, usb.getString(key));
            }
        }
        
        // 解析无线充电数据
        if (json.has("wireless")) {
            JSONObject wireless = json.getJSONObject("wireless");
            Iterator<String> keys = wireless.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                data.wireless.put(key, wireless.getString(key));
            }
        }
        
        return data;
    }
    
    // 电池相关getter方法
    public String getBatteryValue(String key) {
        return battery.get(key);
    }
    
    public int getCapacity() {
        String value = battery.get("capacity");
        return value != null ? Integer.parseInt(value) : -1;
    }
    
    public int getTemperature() {
        String value = battery.get("temp");
        return value != null ? Integer.parseInt(value) : -1;
    }
    
    public long getVoltageNow() {
        String value = battery.get("voltage_now");
        return value != null ? Long.parseLong(value) : -1;
    }
    
    public long getCurrentNow() {
        String value = battery.get("current_now");
        return value != null ? Long.parseLong(value) : -1;
    }
    
    public long getPowerNow() {
        String value = battery.get("power_now");
        return value != null ? Long.parseLong(value) : -1;
    }
    
    public String getStatus() {
        return battery.get("status");
    }
    
    public String getHealth() {
        return battery.get("health");
    }
    
    public String getTechnology() {
        return battery.get("technology");
    }
    
    public long getChargeCounter() {
        String value = battery.get("charge_counter");
        return value != null ? Long.parseLong(value) : -1;
    }
    
    public long getChargeFull() {
        String value = battery.get("charge_full");
        return value != null ? Long.parseLong(value) : -1;
    }
    
    public int getCycleCount() {
        String value = battery.get("cycle_count");
        return value != null ? Integer.parseInt(value) : -1;
    }
    
    public int getChargeStartThreshold() {
        String value = battery.get("charge_control_start_threshold");
        return value != null ? Integer.parseInt(value) : -1;
    }
    
    public int getChargeEndThreshold() {
        String value = battery.get("charge_control_end_threshold");
        return value != null ? Integer.parseInt(value) : -1;
    }
    
    public int getChargeLimit() {
        String value = battery.get("charge_control_limit");
        return value != null ? Integer.parseInt(value) : -1;
    }
    
    // USB相关getter方法
    public String getUsbValue(String key) {
        return usb.get(key);
    }
    
    public boolean isUsbOnline() {
        String value = usb.get("online");
        return value != null && "1".equals(value);
    }
    
    public long getUsbVoltageNow() {
        String value = usb.get("voltage_now");
        return value != null ? Long.parseLong(value) : -1;
    }
    
    public long getUsbCurrentNow() {
        String value = usb.get("current_now");
        return value != null ? Long.parseLong(value) : -1;
    }
    
    public String getUsbType() {
        return usb.get("usb_type");
    }
    
    // 无线充电相关getter方法
    public String getWirelessValue(String key) {
        return wireless.get(key);
    }
    
    public boolean isWirelessOnline() {
        String value = wireless.get("online");
        return value != null && "1".equals(value);
    }
    
    public long getWirelessVoltageNow() {
        String value = wireless.get("voltage_now");
        return value != null ? Long.parseLong(value) : -1;
    }
    
    public long getWirelessCurrentNow() {
        String value = wireless.get("current_now");
        return value != null ? Long.parseLong(value) : -1;
    }
    
    /**
     * 检查是否正在充电
     */
    public boolean isCharging() {
        String status = getStatus();
        return status != null && (status.equals("Charging") || status.equals("Full"));
    }
    
    /**
     * 检查是否为低电量
     */
    public boolean isLowBattery() {
        int capacity = getCapacity();
        return capacity > 0 && capacity <= 20;
    }
    
    /**
     * 获取温度（摄氏度）
     */
    public float getTemperatureCelsius() {
        int temp = getTemperature();
        return temp > 0 ? temp / 10.0f : -1;
    }
    
    /**
     * 获取电压（伏特）
     */
    public float getVoltageVolts() {
        long voltage = getVoltageNow();
        return voltage > 0 ? voltage / 1000000.0f : -1;
    }
    
    /**
     * 获取电流（毫安）
     */
    public float getCurrentMilliamps() {
        long current = getCurrentNow();
        return current != -1 ? current / 1000.0f : -1;
    }
    
    /**
     * 获取功率（毫瓦）
     */
    public float getPowerMilliwatts() {
        long power = getPowerNow();
        return power != -1 ? power / 1000.0f : -1;
    }
    
    @Override
    public String toString() {
        return "BatteryData{" +
                "timestamp=" + timestamp +
                ", capacity=" + getCapacity() + "%" +
                ", status='" + getStatus() + '\'' +
                ", temperature=" + getTemperatureCelsius() + "°C" +
                ", voltage=" + getVoltageVolts() + "V" +
                ", current=" + getCurrentMilliamps() + "mA" +
                '}';
    }
}