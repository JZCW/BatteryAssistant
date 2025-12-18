package com.upo.batteryassistant.data;

/**
 * 电池信息数据类
 */
public class BatteryInfo {
    // 基础信息
    private int level;              // 电量百分比 (0-100)
    private int temperature;        // 温度，单位：0.1°C
    private int voltage;            // 电压，单位：mV
    private int current;            // 电流，单位：mA（负值表示放电）
    private int currentAverage;     // 平均电流，单位：mA
    private int health;             // 健康状态
    private int status;             // 充电状态
    private int plugged;            // 插电方式
    private boolean low;            // 是否低电量
    private long chargeCounter;     // 充电计数器，单位：微安时（μAh）
    private long chargeTimeRemaining; // 剩余充电时间，单位：毫秒（-1表示无法计算）
    private int cycleCount;         // 循环次数

    // Root权限信息
    private int fullCapacity;       // 满电容量，单位：mAh（需要root权限）

  // ========== Root 高级信息扩展字段（直接从 /sys/class/power_supply 读取） ==========
  // battery 电池基础/状态
  private int advBattCapacity;              // capacity（百分比）#
  private String advBattHealthText;         // health 文本（可选覆盖）#
  private String advBattStatusText;         // status 文本 #
  private String advBattTechnology;         // technology #
  private String advBattModelName;          // model_name #
  private int advBattTempDeciC;             // temp，0.1°C
  private int advBattPresent;               // present #
  private String advBattChargeType;         // charge_type #

  // battery 电压/电流/功率（原始单位：一般为微伏/微安/微瓦）
  private long advBattVoltageNowUv;
  private long advBattVoltageMaxUv;
  private long advBattVoltageOcvUv;
  private int advBattCurrentNowUa;
  private int advBattCurrentAvgUa;
  private long advBattPowerNowUw;
  private long advBattPowerAvgUw;

  // battery 容量/寿命
  private long advBattChargeCounterUah;
  private long advBattChargeFullUah;
  private long advBattChargeFullDesignUah;
  private int advBattCycleCount;
  private int advBattTimeToFullNowSec;
  private int advBattTimeToFullAvgSec;
  private int advBattTimeToEmptyAvgSec;

  // battery 充电控制（只读展示）
  private int advBattChargeCtrlStartThr;    // charge_control_start_threshold
  private int advBattChargeCtrlEndThr;      // charge_control_end_threshold
  private int advBattChargeCtrlLimit;       // charge_control_limit
  private int advBattChargeCtrlLimitMax;    // charge_control_limit_max

  // usb 供电信息
  private boolean advUsbOnline;
  private long advUsbVoltageNowUv;
  private long advUsbVoltageMaxUv;
  private int advUsbCurrentNowUa;
  private int advUsbCurrentMaxUa;
  private int advUsbInputCurrentLimitUa;
  private int advUsbTempDeciC;
  private String advUsbType;

  // wireless 供电信息
  private boolean advWlsOnline;
  private long advWlsVoltageNowUv;
  private long advWlsVoltageMaxUv;
  private int advWlsCurrentNowUa;
  private int advWlsCurrentMaxUa;
  private int advWlsInputCurrentLimitUa;
  private int advWlsTempDeciC;

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

    public int getStatus() {
        return status;
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

    public int getPlugged() {
        return plugged;
    }

    public void setPlugged(int plugged) {
        this.plugged = plugged;
    }

    public boolean isLow() {
        return low;
    }

    public void setLow(boolean low) {
        this.low = low;
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

  // ========== Root 高级信息字段 getter / setter ==========

  public int getAdvBattCapacity() {
    return advBattCapacity;
  }

  public void setAdvBattCapacity(int advBattCapacity) {
    this.advBattCapacity = advBattCapacity;
  }

  public String getAdvBattHealthText() {
    return advBattHealthText;
  }

  public void setAdvBattHealthText(String advBattHealthText) {
    this.advBattHealthText = advBattHealthText;
  }

  public String getAdvBattStatusText() {
    return advBattStatusText;
  }

  public void setAdvBattStatusText(String advBattStatusText) {
    this.advBattStatusText = advBattStatusText;
  }

  public String getAdvBattTechnology() {
    return advBattTechnology;
  }

  public void setAdvBattTechnology(String advBattTechnology) {
    this.advBattTechnology = advBattTechnology;
  }

  public String getAdvBattModelName() {
    return advBattModelName;
  }

  public void setAdvBattModelName(String advBattModelName) {
    this.advBattModelName = advBattModelName;
  }

  public int getAdvBattTempDeciC() {
    return advBattTempDeciC;
  }

  public void setAdvBattTempDeciC(int advBattTempDeciC) {
    this.advBattTempDeciC = advBattTempDeciC;
  }

  public long getAdvBattVoltageNowUv() {
    return advBattVoltageNowUv;
  }

  public void setAdvBattVoltageNowUv(long advBattVoltageNowUv) {
    this.advBattVoltageNowUv = advBattVoltageNowUv;
  }

  public long getAdvBattVoltageMaxUv() {
    return advBattVoltageMaxUv;
  }

  public void setAdvBattVoltageMaxUv(long advBattVoltageMaxUv) {
    this.advBattVoltageMaxUv = advBattVoltageMaxUv;
  }

  public long getAdvBattVoltageOcvUv() {
    return advBattVoltageOcvUv;
  }

  public void setAdvBattVoltageOcvUv(long advBattVoltageOcvUv) {
    this.advBattVoltageOcvUv = advBattVoltageOcvUv;
  }

  public int getAdvBattCurrentNowUa() {
    return advBattCurrentNowUa;
  }

  public void setAdvBattCurrentNowUa(int advBattCurrentNowUa) {
    this.advBattCurrentNowUa = advBattCurrentNowUa;
  }

  public int getAdvBattCurrentAvgUa() {
    return advBattCurrentAvgUa;
  }

  public void setAdvBattCurrentAvgUa(int advBattCurrentAvgUa) {
    this.advBattCurrentAvgUa = advBattCurrentAvgUa;
  }

  public long getAdvBattPowerNowUw() {
    return advBattPowerNowUw;
  }

  public void setAdvBattPowerNowUw(long advBattPowerNowUw) {
    this.advBattPowerNowUw = advBattPowerNowUw;
  }

  public long getAdvBattPowerAvgUw() {
    return advBattPowerAvgUw;
  }

  public void setAdvBattPowerAvgUw(long advBattPowerAvgUw) {
    this.advBattPowerAvgUw = advBattPowerAvgUw;
  }

  public long getAdvBattChargeCounterUah() {
    return advBattChargeCounterUah;
  }

  public void setAdvBattChargeCounterUah(long advBattChargeCounterUah) {
    this.advBattChargeCounterUah = advBattChargeCounterUah;
  }

  public long getAdvBattChargeFullUah() {
    return advBattChargeFullUah;
  }

  public void setAdvBattChargeFullUah(long advBattChargeFullUah) {
    this.advBattChargeFullUah = advBattChargeFullUah;
  }

  public long getAdvBattChargeFullDesignUah() {
    return advBattChargeFullDesignUah;
  }

  public void setAdvBattChargeFullDesignUah(long advBattChargeFullDesignUah) {
    this.advBattChargeFullDesignUah = advBattChargeFullDesignUah;
  }

  public int getAdvBattCycleCount() {
    return advBattCycleCount;
  }

  public void setAdvBattCycleCount(int advBattCycleCount) {
    this.advBattCycleCount = advBattCycleCount;
  }

  public int getAdvBattTimeToFullNowSec() {
    return advBattTimeToFullNowSec;
  }

  public void setAdvBattTimeToFullNowSec(int advBattTimeToFullNowSec) {
    this.advBattTimeToFullNowSec = advBattTimeToFullNowSec;
  }

  public int getAdvBattTimeToFullAvgSec() {
    return advBattTimeToFullAvgSec;
  }

  public void setAdvBattTimeToFullAvgSec(int advBattTimeToFullAvgSec) {
    this.advBattTimeToFullAvgSec = advBattTimeToFullAvgSec;
  }

  public int getAdvBattTimeToEmptyAvgSec() {
    return advBattTimeToEmptyAvgSec;
  }

  public void setAdvBattTimeToEmptyAvgSec(int advBattTimeToEmptyAvgSec) {
    this.advBattTimeToEmptyAvgSec = advBattTimeToEmptyAvgSec;
  }

  public int getAdvBattChargeCtrlStartThr() {
    return advBattChargeCtrlStartThr;
  }

  public void setAdvBattChargeCtrlStartThr(int advBattChargeCtrlStartThr) {
    this.advBattChargeCtrlStartThr = advBattChargeCtrlStartThr;
  }

  public int getAdvBattChargeCtrlEndThr() {
    return advBattChargeCtrlEndThr;
  }

  public void setAdvBattChargeCtrlEndThr(int advBattChargeCtrlEndThr) {
    this.advBattChargeCtrlEndThr = advBattChargeCtrlEndThr;
  }

  public int getAdvBattChargeCtrlLimit() {
    return advBattChargeCtrlLimit;
  }

  public void setAdvBattChargeCtrlLimit(int advBattChargeCtrlLimit) {
    this.advBattChargeCtrlLimit = advBattChargeCtrlLimit;
  }

  public int getAdvBattChargeCtrlLimitMax() {
    return advBattChargeCtrlLimitMax;
  }

  public void setAdvBattChargeCtrlLimitMax(int advBattChargeCtrlLimitMax) {
    this.advBattChargeCtrlLimitMax = advBattChargeCtrlLimitMax;
  }

  public String getAdvBattChargeType() {
    return advBattChargeType;
  }

  public void setAdvBattChargeType(String advBattChargeType) {
    this.advBattChargeType = advBattChargeType;
  }

  public int getAdvBattPresent() {
    return advBattPresent;
  }

  public void setAdvBattPresent(int advBattPresent) {
    this.advBattPresent = advBattPresent;
  }

  public boolean isAdvUsbOnline() {
    return advUsbOnline;
  }

  public void setAdvUsbOnline(boolean advUsbOnline) {
    this.advUsbOnline = advUsbOnline;
  }

  public long getAdvUsbVoltageNowUv() {
    return advUsbVoltageNowUv;
  }

  public void setAdvUsbVoltageNowUv(long advUsbVoltageNowUv) {
    this.advUsbVoltageNowUv = advUsbVoltageNowUv;
  }

  public long getAdvUsbVoltageMaxUv() {
    return advUsbVoltageMaxUv;
  }

  public void setAdvUsbVoltageMaxUv(long advUsbVoltageMaxUv) {
    this.advUsbVoltageMaxUv = advUsbVoltageMaxUv;
  }

  public int getAdvUsbCurrentNowUa() {
    return advUsbCurrentNowUa;
  }

  public void setAdvUsbCurrentNowUa(int advUsbCurrentNowUa) {
    this.advUsbCurrentNowUa = advUsbCurrentNowUa;
  }

  public int getAdvUsbCurrentMaxUa() {
    return advUsbCurrentMaxUa;
  }

  public void setAdvUsbCurrentMaxUa(int advUsbCurrentMaxUa) {
    this.advUsbCurrentMaxUa = advUsbCurrentMaxUa;
  }

  public int getAdvUsbInputCurrentLimitUa() {
    return advUsbInputCurrentLimitUa;
  }

  public void setAdvUsbInputCurrentLimitUa(int advUsbInputCurrentLimitUa) {
    this.advUsbInputCurrentLimitUa = advUsbInputCurrentLimitUa;
  }

  public int getAdvUsbTempDeciC() {
    return advUsbTempDeciC;
  }

  public void setAdvUsbTempDeciC(int advUsbTempDeciC) {
    this.advUsbTempDeciC = advUsbTempDeciC;
  }

  public String getAdvUsbType() {
    return advUsbType;
  }

  public void setAdvUsbType(String advUsbType) {
    this.advUsbType = advUsbType;
  }

  public boolean isAdvWlsOnline() {
    return advWlsOnline;
  }

  public void setAdvWlsOnline(boolean advWlsOnline) {
    this.advWlsOnline = advWlsOnline;
  }

  public long getAdvWlsVoltageNowUv() {
    return advWlsVoltageNowUv;
  }

  public void setAdvWlsVoltageNowUv(long advWlsVoltageNowUv) {
    this.advWlsVoltageNowUv = advWlsVoltageNowUv;
  }

  public long getAdvWlsVoltageMaxUv() {
    return advWlsVoltageMaxUv;
  }

  public void setAdvWlsVoltageMaxUv(long advWlsVoltageMaxUv) {
    this.advWlsVoltageMaxUv = advWlsVoltageMaxUv;
  }

  public int getAdvWlsCurrentNowUa() {
    return advWlsCurrentNowUa;
  }

  public void setAdvWlsCurrentNowUa(int advWlsCurrentNowUa) {
    this.advWlsCurrentNowUa = advWlsCurrentNowUa;
  }

  public int getAdvWlsCurrentMaxUa() {
    return advWlsCurrentMaxUa;
  }

  public void setAdvWlsCurrentMaxUa(int advWlsCurrentMaxUa) {
    this.advWlsCurrentMaxUa = advWlsCurrentMaxUa;
  }

  public int getAdvWlsInputCurrentLimitUa() {
    return advWlsInputCurrentLimitUa;
  }

  public void setAdvWlsInputCurrentLimitUa(int advWlsInputCurrentLimitUa) {
    this.advWlsInputCurrentLimitUa = advWlsInputCurrentLimitUa;
  }

  public int getAdvWlsTempDeciC() {
    return advWlsTempDeciC;
  }

  public void setAdvWlsTempDeciC(int advWlsTempDeciC) {
    this.advWlsTempDeciC = advWlsTempDeciC;
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
     * 获取是否正在充电
     */
    public boolean isCharging() {
        return status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
               status == android.os.BatteryManager.BATTERY_STATUS_FULL;
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

