package com.upo.batteryassistant.data;

/**
 * 每日统计数据类
 */
public class DailyStats {
    private String date;
    private int sessionCount;
    private int totalLevelChange;
    private int totalChargeCounterDiff;
    private int estimatedCapacity;
    private int cycleCount;
    private int capacity;
    private int maxLevelChange;

    public DailyStats() {
        // 初始化默认值
        sessionCount = 0;
        totalLevelChange = 0;
        totalChargeCounterDiff = 0;
        estimatedCapacity = -1;
        cycleCount = -1;
        cycleCount = -1;
        maxLevelChange = -1;
    }
    
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }
    public int getSessionCount() { return sessionCount; }
    public void setSessionCount(int sessionCount) { this.sessionCount = sessionCount; }
    public int getTotalLevelChange() { return totalLevelChange; }
    public void setTotalLevelChange(int totalLevelChange) { this.totalLevelChange = totalLevelChange; }
    public int getTotalChargeCounterDiff() { return totalChargeCounterDiff; }
    public void setTotalChargeCounterDiff(int totalChargeCounterDiff) { this.totalChargeCounterDiff = totalChargeCounterDiff; }
    public int getEstimatedCapacity() { return estimatedCapacity; }
    public void setEstimatedCapacity(int estimatedCapacity) { this.estimatedCapacity = estimatedCapacity; }
    public int getCycleCount() { return cycleCount; }
    public void setCycleCount(int cycleCount) { this.cycleCount = cycleCount; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public int getMaxLevelChange() { return maxLevelChange; }
    public void setMaxLevelChange(int maxLevelChange) { this.maxLevelChange = maxLevelChange; }
}
