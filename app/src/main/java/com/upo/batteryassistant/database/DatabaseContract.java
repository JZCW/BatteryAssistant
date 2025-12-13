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
        public static final String COLUMN_START_TIMESTAMP = "start_timestamp";
        public static final String COLUMN_END_TIMESTAMP = "end_timestamp";
        public static final String COLUMN_SESSION_TYPE = "session_type";
        public static final String COLUMN_DURATION = "duration";
        
        // 开始状态
        public static final String COLUMN_START_LEVEL = "start_level";
        public static final String COLUMN_START_TEMPERATURE = "start_temperature";
        public static final String COLUMN_START_VOLTAGE = "start_voltage";
        public static final String COLUMN_START_CURRENT = "start_current";
        
        // 结束状态
        public static final String COLUMN_END_LEVEL = "end_level";
        public static final String COLUMN_END_TEMPERATURE = "end_temperature";
        public static final String COLUMN_END_VOLTAGE = "end_voltage";
        public static final String COLUMN_END_CURRENT = "end_current";
        
        // 统计信息
        public static final String COLUMN_LEVEL_CHANGE = "level_change";
        
        // 阶段类型常量
        public static final int SESSION_TYPE_UNKNOWN = -1;  // 未知阶段
        public static final int SESSION_TYPE_CHARGE = 0;    // 充电阶段
        public static final int SESSION_TYPE_DISCHARGE = 1; // 放电阶段
    }
}

