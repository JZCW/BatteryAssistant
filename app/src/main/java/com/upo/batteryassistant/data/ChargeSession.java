package com.upo.batteryassistant.data;

import java.io.Serializable;

/**
 * 充放电阶段数据类
 */
public class ChargeSession implements Serializable {
    private long id;
    private int sessionType;  // 0=充电, 1=放电
    private long startTimestamp;
    private long endTimestamp;
    private long pauseTimestamp;
    
    // 电量信息
    private int startLevel;           // 开始电量百分比
    private int endLevel;             // 结束电量百分比
    private int startChargeCounter;   // 开始电量(mAh)
    private int endChargeCounter;      // 结束电量(mAh)
    
    // 温度信息
    private int maxTemperature;  // 最高温度(0.1°C)
    private int minTemperature;  // 最低温度(0.1°C)
    
    // 分状态信息
    private long screenOnDuration;         // 屏幕开启时长
    private int screenOnChargeCounterDiff; // 屏幕开启电量变化(mAh,正数为充电,负数为放电)
    private long dozeDuration;          // Doze时长(仅放电)
    private int dozeChargeCounterDiff;  // Doze使用电量(仅放电)
    
    // 估计容量和周期计数（仅充电）
    private int estimatedCapacity;  // 估计容量
    private int cycleCount;         // 周期计数

    // 进行中状态
    private boolean isOngoing;      // 是否进行中
    
    public ChargeSession() {
        // 初始化默认值
        pauseTimestamp = 0;
        screenOnDuration = 0;
        screenOnChargeCounterDiff = 0;
        dozeDuration = 0;
        dozeChargeCounterDiff = 0;
    }
    
    // Getters and Setters
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    public int getSessionType() {
        return sessionType;
    }
    
    public void setSessionType(int sessionType) {
        this.sessionType = sessionType;
    }
    
    public long getStartTimestamp() {
        return startTimestamp;
    }
    
    public void setStartTimestamp(long startTimestamp) {
        this.startTimestamp = startTimestamp;
    }
    
    public long getEndTimestamp() {
        return endTimestamp;
    }
    
    public void setEndTimestamp(long endTimestamp) {
        this.endTimestamp = endTimestamp;
    }
    
    public long getPauseTimestamp() {
        return pauseTimestamp;
    }
    
    public void setPauseTimestamp(long pauseTimestamp) {
        this.pauseTimestamp = pauseTimestamp;
    }
    
    public int getStartLevel() {
        return startLevel;
    }
    
    public void setStartLevel(int startLevel) {
        this.startLevel = startLevel;
    }
    
    public int getEndLevel() {
        return endLevel;
    }
    
    public void setEndLevel(int endLevel) {
        this.endLevel = endLevel;
    }
    
    public int getStartChargeCounter() {
        return startChargeCounter;
    }
    
    public void setStartChargeCounter(int startChargeCounter) {
        this.startChargeCounter = startChargeCounter;
    }
    
    public int getEndChargeCounter() {
        return endChargeCounter;
    }
    
    public void setEndChargeCounter(int endChargeCounter) {
        this.endChargeCounter = endChargeCounter;
    }
    
    public int getMaxTemperature() {
        return maxTemperature;
    }
    
    public void setMaxTemperature(int maxTemperature) {
        this.maxTemperature = maxTemperature;
    }
    
    public int getMinTemperature() {
        return minTemperature;
    }
    
    public void setMinTemperature(int minTemperature) {
        this.minTemperature = minTemperature;
    }
    
    public long getScreenOnDuration() {
        return screenOnDuration;
    }
    
    public void setScreenOnDuration(long screenOnDuration) {
        this.screenOnDuration = screenOnDuration;
    }
    
    public int getScreenOnChargeCounterDiff() {
        return screenOnChargeCounterDiff;
    }
    
    public void setScreenOnChargeCounterDiff(int screenOnChargeCounterDiff) {
        this.screenOnChargeCounterDiff = screenOnChargeCounterDiff;
    }
    
    public long getDozeDuration() {
        return dozeDuration;
    }
    
    public void setDozeDuration(long dozeDuration) {
        this.dozeDuration = dozeDuration;
    }
    
    public int getDozeChargeCounterDiff() {
        return dozeChargeCounterDiff;
    }
    
    public void setDozeChargeCounterDiff(int dozeChargeCounterDiff) {
        this.dozeChargeCounterDiff = dozeChargeCounterDiff;
    }
    
    public int getEstimatedCapacity() {
        return estimatedCapacity;
    }
    
    public void setEstimatedCapacity(int estimatedCapacity) {
        this.estimatedCapacity = estimatedCapacity;
    }
    
    public int getCycleCount() {
        return cycleCount;
    }

    public void setCycleCount(int cycleCount) {
        this.cycleCount = cycleCount;
    }

    public boolean isOngoing() {
        return isOngoing;
    }

    public void setOngoing(boolean ongoing) {
        isOngoing = ongoing;
    }
    
    /**
     * 获取电量变化（百分比）
     */
    public int getLevelChange() {
        return endLevel - startLevel;
    }
    
    /**
     * 获取电量变化（mAh）
     */
    public int getChargeCounterDiff() {
        if (startChargeCounter < 0 || endChargeCounter < 0) {
            return 0;
        }
        return endChargeCounter - startChargeCounter;
    }
    
    /**
     * 获取阶段类型文本
     */
    public String getSessionTypeText() {
        return sessionType == 0 ? "充电" : "放电";
    }
    
    /**
     * 获取最高温度（摄氏度）
     */
    public float getMaxTemperatureCelsius() {
        return maxTemperature / 10.0f;
    }
    
    /**
     * 获取最低温度（摄氏度）
     */
    public float getMinTemperatureCelsius() {
        return minTemperature / 10.0f;
    }
    
    /**
     * 合并两个会话（当前会话在前，传入会话在后）
     * @param laterSession 后续会话
     */
    public void mergeWith(ChargeSession laterSession) {
        // 更新结束状态为后续会话的结束状态
        this.endTimestamp = laterSession.endTimestamp;
        this.endLevel = laterSession.endLevel;
        this.endChargeCounter = laterSession.endChargeCounter;
        this.estimatedCapacity = laterSession.estimatedCapacity;
        this.cycleCount = laterSession.cycleCount;
        
        // 更新温度统计
        if (laterSession.maxTemperature > this.maxTemperature) {
            this.maxTemperature = laterSession.maxTemperature;
        }
        if (laterSession.minTemperature < this.minTemperature || this.minTemperature <= 0) {
            this.minTemperature = laterSession.minTemperature;
        }
        
        // 累加暂停时间
        this.pauseTimestamp += laterSession.pauseTimestamp;
        
        // 累加屏幕相关数据
        this.screenOnDuration += laterSession.screenOnDuration;
        this.screenOnChargeCounterDiff += laterSession.screenOnChargeCounterDiff;
        
        // 累加Doze相关数据
        this.dozeDuration += laterSession.dozeDuration;
        this.dozeChargeCounterDiff += laterSession.dozeChargeCounterDiff;
    }
}

