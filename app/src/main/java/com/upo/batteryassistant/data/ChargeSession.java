package com.upo.batteryassistant.data;

/**
 * 充放电阶段数据类
 */
public class ChargeSession {
    private long id;
    private long startTimestamp;
    private long endTimestamp;
    private int sessionType;  // 0=充电, 1=放电
    private long duration;    // 持续时间（毫秒）
    
    // 开始状态
    private int startLevel;
    private int startTemperature;
    private int startVoltage;
    private int startCurrent;
    
    // 结束状态
    private int endLevel;
    private int endTemperature;
    private int endVoltage;
    private int endCurrent;
    
    // 统计信息
    private int levelChange;  // 电量变化（百分比）
    
    public ChargeSession() {
    }
    
    // Getters and Setters
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
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
    
    public int getSessionType() {
        return sessionType;
    }
    
    public void setSessionType(int sessionType) {
        this.sessionType = sessionType;
    }
    
    public long getDuration() {
        return duration;
    }
    
    public void setDuration(long duration) {
        this.duration = duration;
    }
    
    public int getStartLevel() {
        return startLevel;
    }
    
    public void setStartLevel(int startLevel) {
        this.startLevel = startLevel;
    }
    
    public int getStartTemperature() {
        return startTemperature;
    }
    
    public void setStartTemperature(int startTemperature) {
        this.startTemperature = startTemperature;
    }
    
    public int getStartVoltage() {
        return startVoltage;
    }
    
    public void setStartVoltage(int startVoltage) {
        this.startVoltage = startVoltage;
    }
    
    public int getStartCurrent() {
        return startCurrent;
    }
    
    public void setStartCurrent(int startCurrent) {
        this.startCurrent = startCurrent;
    }
    
    public int getEndLevel() {
        return endLevel;
    }
    
    public void setEndLevel(int endLevel) {
        this.endLevel = endLevel;
    }
    
    public int getEndTemperature() {
        return endTemperature;
    }
    
    public void setEndTemperature(int endTemperature) {
        this.endTemperature = endTemperature;
    }
    
    public int getEndVoltage() {
        return endVoltage;
    }
    
    public void setEndVoltage(int endVoltage) {
        this.endVoltage = endVoltage;
    }
    
    public int getEndCurrent() {
        return endCurrent;
    }
    
    public void setEndCurrent(int endCurrent) {
        this.endCurrent = endCurrent;
    }
    
    public int getLevelChange() {
        return levelChange;
    }
    
    public void setLevelChange(int levelChange) {
        this.levelChange = levelChange;
    }
    
    /**
     * 获取阶段类型文本
     */
    public String getSessionTypeText() {
        return sessionType == 0 ? "充电" : "放电";
    }
    
    /**
     * 获取持续时间文本（小时:分钟）
     */
    public String getDurationText() {
        long hours = duration / (60 * 60 * 1000);
        long minutes = (duration % (60 * 60 * 1000)) / (60 * 1000);
        if (hours > 0) {
            return String.format("%d小时%d分钟", hours, minutes);
        } else {
            return String.format("%d分钟", minutes);
        }
    }
    
    /**
     * 获取开始温度（摄氏度）
     */
    public float getStartTemperatureCelsius() {
        return startTemperature / 10.0f;
    }
    
    /**
     * 获取结束温度（摄氏度）
     */
    public float getEndTemperatureCelsius() {
        return endTemperature / 10.0f;
    }
    
    /**
     * 获取开始电压（伏特）
     */
    public float getStartVoltageVolts() {
        return startVoltage / 1000.0f;
    }
    
    /**
     * 获取结束电压（伏特）
     */
    public float getEndVoltageVolts() {
        return endVoltage / 1000.0f;
    }
}

