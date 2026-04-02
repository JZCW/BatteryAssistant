package com.upo.batteryassistant.data;

import android.util.Log;

/**
 * 电池信息数据类
 */
public class BatteryInfo {
    // 基础信息
    private long timestamp = 0;
    private int level = -1;                // 电量百分比 (0-100)
    private int temperature = -1;          // 温度，单位：0.1°C
    private int voltage = -1;              // 电压，单位：mV
    private int current = Integer.MIN_VALUE;              // 电流，单位：mA（负值表示放电）
    private int currentAverage = Integer.MIN_VALUE;       // 平均电流，单位：mA
    private int health = -1;               // 健康度
    private int health_api = -1;           // API健康状态
    private int status = -1;               // 充电状态
    private int chargeCounter = -1;        // 充电计数器，单位：毫安时（mAh）
    private long chargeTimeRemaining = -1; // 剩余充电时间，单位：毫秒（-1表示无法计算）
    private int cycleCount = -1;           // 循环次数
    private int fullCapacity = -1;         // 满电容量，单位：mAh
    private int designCapacity = -1;       // 设计容量，单位：mAh

    // 高级信息
    private boolean usb_online = false;             // USB在线状态
    private int usb_voltage_now = -1;        // voltage in mV
    private int usb_voltage_max = -1;        // max voltage in mV
    private int usb_current_max = Integer.MIN_VALUE;        // max current in mA
    private String usb_type = "";       // [Unknown] SDP DCP CDP ACA C PD PD_DRP PD_PPS BrickID
    private boolean wireless_online = false;
    private int wireless_voltage_now = -1;    // voltage in mV
    private int wireless_voltage_max = -1;    // max voltage in mV
    private int wireless_current_max = Integer.MIN_VALUE;    // max current in mA
    private String wireless_type = "";       // [Unknown] BPP
    private int in_current_now = Integer.MIN_VALUE;        // current in mA // usb与wls相同数据
    private int scenario_fcc = -1;           // 场景快速充电电流(mA)

    public BatteryInfo() {}

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getTemperature() {
        return temperature;
    }

    public void setTemperature(int temperature) {
        this.temperature = temperature;
    }

    public int getVoltage() {
        return voltage;
    }

    public void setVoltage(int voltage) {
        this.voltage = voltage;
    }

    public int getCurrent() {
        return current;
    }

    public void setCurrent(int current) {
        this.current = current;
    }

    public int getHealth() {
        return health;
    }

    public void setHealth(int health) {
        this.health = health;
    }

    public void setHealthApi(int health) {
        this.health_api = health;
    }

    public String getHealthText() {
        switch (health_api) {
            case android.os.BatteryManager.BATTERY_HEALTH_GOOD:
                return "良好";
            case android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT:
                return "过热";
            case android.os.BatteryManager.BATTERY_HEALTH_DEAD:
                return "已损坏";
            case android.os.BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                return "过压";
            case android.os.BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                return "未知故障";
            case android.os.BatteryManager.BATTERY_HEALTH_COLD:
                return "过冷";
            default:
                return "未知";
        }
    }

    public String getStatusText() {
        switch (status) {
            case android.os.BatteryManager.BATTERY_STATUS_CHARGING:
                return "充电中";
            case android.os.BatteryManager.BATTERY_STATUS_DISCHARGING:
                return "放电中";
            case android.os.BatteryManager.BATTERY_STATUS_FULL:
                return "已充满";
            case android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return "未充电";
            case android.os.BatteryManager.BATTERY_STATUS_UNKNOWN:
                return "未知";
            default:
                Log.e("BatteryInfo", "Unknown status: " + status);
                return "未知";
        }
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getCurrentAverage() {
        return currentAverage;
    }

    public void setCurrentAverage(int currentAverage) {
        this.currentAverage = currentAverage;
    }

    public int getChargeCounter() {
        return chargeCounter;
    }

    public void setChargeCounter(int chargeCounter) {
        this.chargeCounter = chargeCounter;
    }

    public long getChargeTimeRemaining() {
        return chargeTimeRemaining;
    }

    public void setChargeTimeRemaining(long chargeTimeRemaining) {
        this.chargeTimeRemaining = chargeTimeRemaining;
    }

    public int getCycleCount() {
        return cycleCount;
    }

    public void setCycleCount(int cycleCount) {
        this.cycleCount = cycleCount;
    }

    public int getFullCapacity() {
        return fullCapacity;
    }

    public void setFullCapacity(int fullCapacity) {
        this.fullCapacity = fullCapacity;
    }

    public int getDesignCapacity() {
        return designCapacity;
    }

    public void setDesignCapacity(int designCapacity) {
        this.designCapacity = designCapacity;
    }

    public boolean isUsbOnline() {
        return usb_online;
    }

    public void setUsbOnline(boolean usb_online) {
        this.usb_online = usb_online;
    }

    public int getUsbVoltageNow() {
        return usb_voltage_now;
    }

    public void setUsbVoltageNow(int usb_voltage_now) {
        this.usb_voltage_now = usb_voltage_now;
    }

    public int getUsbVoltageMax() {
        return usb_voltage_max;
    }

    public void setUsbVoltageMax(int usb_voltage_max) {
        this.usb_voltage_max = usb_voltage_max;
    }

    public int getUsbCurrentMax() {
        return usb_current_max;
    }

    public void setUsbCurrentMax(int usb_current_max) {
        this.usb_current_max = usb_current_max;
    }

    public String getUsbType() {
        return usb_type;
    }

    public void setUsbType(String usb_type) {
        this.usb_type = usb_type;
    }

    public boolean isWirelessOnline() {
        return wireless_online;
    }

    public void setWirelessOnline(boolean wireless_online) {
        this.wireless_online = wireless_online;
    }

    public int getWirelessVoltageNow() {
        return wireless_voltage_now;
    }

    public void setWirelessVoltageNow(int wireless_voltage_now) {
        this.wireless_voltage_now = wireless_voltage_now;
    }

    public int getWirelessVoltageMax() {
        return wireless_voltage_max;
    }

    public void setWirelessVoltageMax(int wireless_voltage_max) {
        this.wireless_voltage_max = wireless_voltage_max;
    }

    public int getWirelessCurrentMax() {
        return wireless_current_max;
    }

    public void setWirelessCurrentMax(int wireless_current_max) {
        this.wireless_current_max = wireless_current_max;
    }

    public String getWirelessType() {
        return wireless_type;
    }

    public void setWirelessType(String wireless_type) {
        this.wireless_type = wireless_type;
    }

    public int getInCurrentNow() {
        return in_current_now;
    }

    public void setInCurrentNow(int in_current_now) {
        this.in_current_now = in_current_now;
    }

    public int getScenarioFcc() {
        return scenario_fcc;
    }

    public void setScenarioFcc(int scenario_fcc) {
        this.scenario_fcc = scenario_fcc;
    }

    /**
     * 获取温度（摄氏度）
     */
    public float getTemperatureCelsius() {
        return temperature / 10.0f;
    }

    /**
     * 获取电压（伏特）
     */
    public float getVoltageVolts() {
        return voltage / 1000.0f;
    }

    /**
     * 获取是否正在充电
     */
    public boolean isCharging() {
        return status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
               status == android.os.BatteryManager.BATTERY_STATUS_FULL;
    }

    /**
     * 获取剩余充电时间文本
     */
    public String getChargeTimeRemainingText() {
        if (chargeTimeRemaining < 0) {
            return "无法计算";
        }
        long hours = chargeTimeRemaining / (60 * 60 * 1000);
        long minutes = (chargeTimeRemaining % (60 * 60 * 1000)) / (60 * 1000);
        if (hours > 0) {
            return String.format("%d小时%d分钟", hours, minutes);
        } else {
            return String.format("%d分钟", minutes);
        }
    }
}

