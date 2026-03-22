package com.upo.batteryassistant.ui.stats;

/**
 * UI层使用的统计条目模型。
 */
public class StatsEntry {
    private final String periodLabel;
    private final String periodDescription;
    private final int sessionCount;
    private final int totalLevelChange;
    private final int totalChargeCounterDiff;
    private final int estimatedCapacity;
    private final int cycleCount;
    private final int capacity;
    private final int maxLevelChange;

    public StatsEntry(String periodLabel,
                      String periodDescription,
                      int sessionCount,
                      int totalLevelChange,
                      int totalChargeCounterDiff,
                      int estimatedCapacity,
                      int cycleCount,
                      int capacity,
                      int maxLevelChange) {
        this.periodLabel = periodLabel;
        this.periodDescription = periodDescription;
        this.sessionCount = sessionCount;
        this.totalLevelChange = totalLevelChange;
        this.totalChargeCounterDiff = totalChargeCounterDiff;
        this.estimatedCapacity = estimatedCapacity;
        this.cycleCount = cycleCount;
        this.capacity = capacity;
        this.maxLevelChange = maxLevelChange;
    }

    public String getPeriodLabel() {
        return periodLabel;
    }

    public String getPeriodDescription() {
        return periodDescription;
    }

    public int getSessionCount() {
        return sessionCount;
    }

    public int getTotalLevelChange() {
        return totalLevelChange;
    }

    public int getTotalChargeCounterDiff() {
        return totalChargeCounterDiff;
    }

    public int getEstimatedCapacity() {
        return estimatedCapacity;
    }

    public int getCycleCount() {
        return cycleCount;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getMaxLevelChange() {
        return maxLevelChange;
    }
}
