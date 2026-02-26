package com.upo.batteryassistant.data;

import android.util.Log;

/**
 * 电池信息数据类
 */
public class BatteryInfo {
    // 基础信息
    private int level;                // 电量百分比 (0-100)
    private int temperature;          // 温度，单位：0.1°C
    private int voltage;              // 电压，单位：mV
    private int current;              // 电流，单位：mA（负值表示放电）
    private int currentAverage;       // 平均电流，单位：mA
    private int health;               // 健康状态 //FIXME API读到的是枚举值
    private int status;               // 充电状态
    private int chargeCounter;        // 充电计数器，单位：毫安时（mAh）
    private long chargeTimeRemaining; // 剩余充电时间，单位：毫秒（-1表示无法计算）
    private int cycleCount;           // 循环次数
    private int fullCapacity;         // 满电容量，单位：mAh
    private int designCapacity;       // 设计容量，单位：mAh

    public BatteryInfo() {
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

    // /**
    //  * 获取健康状态文本
    //  */
    // public String getHealthText() {
    //     switch (health) {
    //         case android.os.BatteryManager.BATTERY_HEALTH_GOOD:
    //             return "良好";
    //         case android.os.BatteryManager.BATTERY_HEALTH_OVERHEAT:
    //             return "过热";
    //         case android.os.BatteryManager.BATTERY_HEALTH_DEAD:
    //             return "已损坏";
    //         case android.os.BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
    //             return "过压";
    //         case android.os.BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
    //             return "未知故障";
    //         case android.os.BatteryManager.BATTERY_HEALTH_COLD:
    //             return "过冷";
    //         default:
    //             return "未知";
    //     }
    // }

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

