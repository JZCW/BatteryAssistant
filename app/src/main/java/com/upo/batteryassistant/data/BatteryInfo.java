package com.upo.batteryassistant.data;

/**
 * 电池信息数据类
 */
public class BatteryInfo {
    private int level;              // 电量百分比 (0-100)
    private boolean isCharging;    // 是否正在充电
    private int temperature;        // 温度，单位：0.1°C
    private int voltage;            // 电压，单位：mV
    private int current;            // 电流，单位：mA（负值表示放电）
    private int health;             // 健康状态
    private int status;             // 充电状态
    private String technology;      // 电池技术类型
    private int fullCapacity;       // 满电容量，单位：mAh（需要root权限）
    private int cycleCount;         // 循环次数（需要root权限）

    public BatteryInfo() {
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public boolean isCharging() {
        return isCharging;
    }

    public void setCharging(boolean charging) {
        isCharging = charging;
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

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getTechnology() {
        return technology;
    }

    public void setTechnology(String technology) {
        this.technology = technology;
    }

    public int getFullCapacity() {
        return fullCapacity;
    }

    public void setFullCapacity(int fullCapacity) {
        this.fullCapacity = fullCapacity;
    }

    public int getCycleCount() {
        return cycleCount;
    }

    public void setCycleCount(int cycleCount) {
        this.cycleCount = cycleCount;
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
     * 获取健康状态文本
     */
    public String getHealthText() {
        switch (health) {
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

    /**
     * 获取充电状态文本
     */
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
            default:
                return "未知";
        }
    }
}

