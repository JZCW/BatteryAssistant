package com.upo.batteryassistant.database;

/**
 * 数据库表结构常量定义
 */
public final class DatabaseContract {
    private DatabaseContract() {}
    
    // 合并策略常量
    public static final long MERGE_DIFFERENT_TYPE_THRESHOLD = 60 * 1000;  // 1分钟（毫秒）
    public static final long MERGE_SAME_TYPE_INTERVAL_THRESHOLD = 5 * 60 * 1000;  // 5分钟（毫秒）
    public static final int MERGE_SAME_TYPE_LEVEL_DIFF_THRESHOLD = 1;  // 1%
    
    /**
     * 充放电阶段表
     */
    public static class ChargeSessionEntry {
        public static final String TABLE_NAME = "charge_sessions";
        
        public static final String COLUMN_ID = "_id";
        public static final String COLUMN_SESSION_TYPE = "session_type";
        public static final String COLUMN_START_TIMESTAMP = "start_timestamp";
        public static final String COLUMN_END_TIMESTAMP = "end_timestamp";
        public static final String COLUMN_PAUSE_DURATION = "pause_duration";
        public static final String COLUMN_DURATION = "duration";
        
        // 电量信息
        public static final String COLUMN_START_LEVEL = "start_level";
        public static final String COLUMN_END_LEVEL = "end_level";
        public static final String COLUMN_START_CHARGE_COUNTER = "start_charge_counter";
        public static final String COLUMN_END_CHARGE_COUNTER = "end_charge_counter";
        
        // 温度信息
        public static final String COLUMN_MAX_TEMPERATURE = "max_temperature";
        public static final String COLUMN_MIN_TEMPERATURE = "min_temperature";
        
        // 屏幕相关信息（未实现）
        public static final String COLUMN_SCREEN_ON_DURATION = "screen_on_duration";
        public static final String COLUMN_SCREEN_ON_LEVEL_CHANGE = "screen_on_level_change";
        public static final String COLUMN_SCREEN_ON_CHARGE_COUNTER_DIFF = "screen_on_charge_counter_diff";
        
        // Doze相关信息（未实现）
        public static final String COLUMN_DOZE_DURATION = "doze_duration";
        public static final String COLUMN_DOZE_CHARGE_COUNTER_DIFF = "doze_charge_counter_diff";
        
        // 估计容量和周期计数（仅充电）
        public static final String COLUMN_ESTIMATED_CAPACITY = "estimated_capacity";
        public static final String COLUMN_CYCLE_COUNT = "cycle_count";
        
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
    }
    
    /**
     * 每周统计表
     */
    public static class WeeklyStatsEntry {
        public static final String TABLE_NAME = "weekly_stats";
        
        public static final String COLUMN_ID = "_id";
        public static final String COLUMN_WEEK_START = "week_start";  // YYYY-MM-DD (周一日期)
        public static final String COLUMN_SESSION_COUNT = "session_count";
        public static final String COLUMN_TOTAL_LEVEL_CHANGE = "total_level_change";
        public static final String COLUMN_TOTAL_CHARGE_COUNTER_DIFF = "total_charge_counter_diff";
        public static final String COLUMN_ESTIMATED_CAPACITY = "estimated_capacity";
        public static final String COLUMN_CYCLE_COUNT = "cycle_count";
    }
    
    /**
     * 每月统计表
     */
    public static class MonthlyStatsEntry {
        public static final String TABLE_NAME = "monthly_stats";
        
        public static final String COLUMN_ID = "_id";
        public static final String COLUMN_YEAR_MONTH = "year_month";  // YYYY-MM
        public static final String COLUMN_SESSION_COUNT = "session_count";
        public static final String COLUMN_TOTAL_LEVEL_CHANGE = "total_level_change";
        public static final String COLUMN_TOTAL_CHARGE_COUNTER_DIFF = "total_charge_counter_diff";
        public static final String COLUMN_ESTIMATED_CAPACITY = "estimated_capacity";
        public static final String COLUMN_CYCLE_COUNT = "cycle_count";
    }
}

