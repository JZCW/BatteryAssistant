package com.upo.batteryassistant.database;

/**
 * 数据库表结构常量定义
 */
public final class DatabaseContract {
    private DatabaseContract() {}
    
    /**
     * 充放电阶段表
     */
    public static class ChargeSessionEntry {
        public static final String TABLE_NAME = "charge_sessions";
        
        public static final String COLUMN_ID = "_id";
        public static final String COLUMN_SESSION_TYPE = "session_type";
        public static final String COLUMN_START_TIMESTAMP = "start_timestamp";
        public static final String COLUMN_END_TIMESTAMP = "end_timestamp";
        public static final String COLUMN_PAUSE_TIMESTAMP = "pause_timestamp";
        
        // 电量信息
        public static final String COLUMN_START_LEVEL = "start_level";
        public static final String COLUMN_END_LEVEL = "end_level";
        public static final String COLUMN_START_CHARGE_COUNTER = "start_charge_counter";
        public static final String COLUMN_END_CHARGE_COUNTER = "end_charge_counter";
        
        // 温度信息
        public static final String COLUMN_MAX_TEMPERATURE = "max_temperature";
        public static final String COLUMN_MIN_TEMPERATURE = "min_temperature";
        
        // 屏幕相关信息
        public static final String COLUMN_SCREEN_ON_DURATION = "screen_on_duration";
        public static final String COLUMN_SCREEN_ON_CHARGE_COUNTER_DIFF = "screen_on_charge_counter_diff";
        
        // Doze相关信息
        public static final String COLUMN_DOZE_DURATION = "doze_duration";
        public static final String COLUMN_DOZE_CHARGE_COUNTER_DIFF = "doze_charge_counter_diff";
        
        // 估计容量和周期计数
        public static final String COLUMN_ESTIMATED_CAPACITY = "estimated_capacity";
        public static final String COLUMN_CYCLE_COUNT = "cycle_count";

        // 进行中状态
        public static final String COLUMN_IS_ONGOING = "is_ongoing";
        
        // 更新计数
        public static final String COLUMN_COUNTER = "counter";
        
        // 阶段类型常量
        public static final int SESSION_TYPE_UNKNOWN = -1;  // 未知阶段
        public static final int SESSION_TYPE_CHARGE = 0;    // 充电阶段
        public static final int SESSION_TYPE_DISCHARGE = 1; // 放电阶段
    }
    
    /**
     * 每日统计表
     */
    public static class DailyStatsEntry {
        public static final String TABLE_NAME = "daily_stats";
        
        public static final String COLUMN_ID = "_id";
        public static final String COLUMN_DATE = "date";  // YYYY-MM-DD
        public static final String COLUMN_SESSION_COUNT = "session_count";
        public static final String COLUMN_TOTAL_LEVEL_CHANGE = "total_level_change";
        public static final String COLUMN_TOTAL_CHARGE_COUNTER_DIFF = "total_charge_counter_diff";
        public static final String COLUMN_ESTIMATED_CAPACITY = "estimated_capacity";
        public static final String COLUMN_CYCLE_COUNT = "cycle_count";
        public static final String COLUMN_CAPACITY = "capacity";
        public static final String COLUMN_MAX_LEVEL_CHANGE = "max_level_change";
    }
}

