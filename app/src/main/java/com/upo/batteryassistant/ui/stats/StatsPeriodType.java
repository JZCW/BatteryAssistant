package com.upo.batteryassistant.ui.stats;

public enum StatsPeriodType {
    DAILY,
    WEEKLY,
    MONTHLY;

    public static StatsPeriodType fromOrdinal(int ordinal) {
        StatsPeriodType[] values = values();
        if (ordinal < 0 || ordinal >= values.length) {
            return DAILY;
        }
        return values[ordinal];
    }
}
