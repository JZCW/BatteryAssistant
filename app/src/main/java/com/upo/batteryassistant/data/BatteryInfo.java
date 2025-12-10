package com.upo.batteryassistant.data;

/**
 * 电池信息数据类
 */
public class BatteryInfo {
    // 基础信息
    private int level;              // 电量百分比 (0-100)
    private int scale;              // 电量最大值
    private boolean isCharging;    // 是否正在充电
    private int temperature;        // 温度，单位：0.1°C
    private int voltage;            // 电压，单位：mV
    private int current;            // 电流，单位：mA（负值表示放电）
    private int currentAverage;     // 平均电流，单位：mA
    private int health;             // 健康状态
    private int status;             // 充电状态
    private int plugged;            // 插电方式
    private boolean present;        // 电池是否存在
    private String technology;      // 电池技术类型
    private boolean low;            // 是否低电量（API 28+）
    
    // 高级信息（API 21+）
    private int capacity;           // 电量百分比（通过BatteryManager获取）
    private long energyCounter;     // 剩余能量，单位：纳瓦时（nWh）
    private long chargeCounter;     // 充电计数器，单位：微安时（μAh）
    private long chargeTimeRemaining; // 剩余充电时间，单位：毫秒（-1表示无法计算）
    
    // 新API信息（API 34+）
    private int chargingStatus;     // 充电状态（API 34+）
    private int cycleCount;         // 循环次数（API 34+）
    private int capacityLevel;      // 容量级别（API 36+）
    
    // Root权限信息
    private int fullCapacity;       // 满电容量，单位：mAh（需要root权限）

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
    
    public int getScale() {
        return scale;
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public int getCurrentAverage() {
        return currentAverage;
    }

    public void setCurrentAverage(int currentAverage) {
        this.currentAverage = currentAverage;
    }

    public int getPlugged() {
        return plugged;
    }

    public void setPlugged(int plugged) {
        this.plugged = plugged;
    }

    public boolean isPresent() {
        return present;
    }

    public void setPresent(boolean present) {
        this.present = present;
    }

    public boolean isLow() {
        return low;
    }

    public void setLow(boolean low) {
        this.low = low;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public long getEnergyCounter() {
        return energyCounter;
    }

    public void setEnergyCounter(long energyCounter) {
        this.energyCounter = energyCounter;
    }

    public long getChargeCounter() {
        return chargeCounter;
    }

    public void setChargeCounter(long chargeCounter) {
        this.chargeCounter = chargeCounter;
    }

    public long getChargeTimeRemaining() {
        return chargeTimeRemaining;
    }

    public void setChargeTimeRemaining(long chargeTimeRemaining) {
        this.chargeTimeRemaining = chargeTimeRemaining;
    }

    public int getChargingStatus() {
        return chargingStatus;
    }

    public void setChargingStatus(int chargingStatus) {
        this.chargingStatus = chargingStatus;
    }

    public int getCycleCount() {
        return cycleCount;
    }

    public void setCycleCount(int cycleCount) {
        this.cycleCount = cycleCount;
    }

    public int getCapacityLevel() {
        return capacityLevel;
    }

    public void setCapacityLevel(int capacityLevel) {
        this.capacityLevel = capacityLevel;
    }

    public int getFullCapacity() {
        return fullCapacity;
    }

    public void setFullCapacity(int fullCapacity) {
        this.fullCapacity = fullCapacity;
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
     * 获取电流（毫安）
     */
    public float getCurrentMilliAmps() {
        return current / 1000.0f;
    }

    /**
     * 获取平均电流（毫安）
     */
    public float getCurrentAverageMilliAmps() {
        return currentAverage / 1000.0f;
    }

    /**
     * 获取剩余能量（瓦时）
     */
    public float getEnergyCounterWattHours() {
        return energyCounter / 1_000_000_000_000.0f; // 纳瓦时转瓦时
    }

    /**
     * 获取充电计数器（毫安时）
     */
    public float getChargeCounterMilliAmpHours() {
        return chargeCounter / 1000.0f; // 微安时转毫安时
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
            case android.os.BatteryManager.BATTERY_STATUS_UNKNOWN:
                return "未知";
            default:
                return "未知";
        }
    }

    /**
     * 获取插电方式文本
     */
    public String getPluggedText() {
        switch (plugged) {
            case android.os.BatteryManager.BATTERY_PLUGGED_USB:
                return "USB";
            case android.os.BatteryManager.BATTERY_PLUGGED_AC:
                return "交流电";
            case android.os.BatteryManager.BATTERY_PLUGGED_WIRELESS:
                return "无线充电";
            case android.os.BatteryManager.BATTERY_PLUGGED_DOCK:
                return "底座";
            case 0:
                return "未插电";
            default:
                return "未知";
        }
    }

    /**
     * 获取容量级别文本（API 36+）
     */
    public String getCapacityLevelText() {
        switch (capacityLevel) {
            case android.os.BatteryManager.BATTERY_CAPACITY_LEVEL_UNSUPPORTED:
                return "不支持";
            case android.os.BatteryManager.BATTERY_CAPACITY_LEVEL_UNKNOWN:
                return "未知";
            case android.os.BatteryManager.BATTERY_CAPACITY_LEVEL_CRITICAL:
                return "严重";
            case android.os.BatteryManager.BATTERY_CAPACITY_LEVEL_LOW:
                return "低";
            case android.os.BatteryManager.BATTERY_CAPACITY_LEVEL_NORMAL:
                return "正常";
            case android.os.BatteryManager.BATTERY_CAPACITY_LEVEL_HIGH:
                return "高";
            case android.os.BatteryManager.BATTERY_CAPACITY_LEVEL_FULL:
                return "满";
            default:
                return "未知";
        }
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

