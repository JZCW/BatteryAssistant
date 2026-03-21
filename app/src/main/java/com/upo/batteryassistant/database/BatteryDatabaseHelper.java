package com.upo.batteryassistant.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.upo.batteryassistant.data.ChargeSession;
import com.upo.batteryassistant.data.DailyStats;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 电池数据库Helper
 */
public class BatteryDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "BatteryDatabaseHelper";
    private static final String DATABASE_NAME = "battery_assistant.db";
    private static final int DATABASE_VERSION = 8;
    
    // 创建charge_sessions表的SQL
    private static final String SQL_CREATE_CHARGE_SESSIONS_TABLE =
        "CREATE TABLE " + DatabaseContract.ChargeSessionEntry.TABLE_NAME + " (" +
        DatabaseContract.ChargeSessionEntry.COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
        DatabaseContract.ChargeSessionEntry.COLUMN_SESSION_TYPE + " INTEGER NOT NULL," +
        DatabaseContract.ChargeSessionEntry.COLUMN_START_TIMESTAMP + " INTEGER NOT NULL," +
        DatabaseContract.ChargeSessionEntry.COLUMN_END_TIMESTAMP + " INTEGER NOT NULL," +
        DatabaseContract.ChargeSessionEntry.COLUMN_PAUSE_TIMESTAMP + " INTEGER DEFAULT 0," +
        DatabaseContract.ChargeSessionEntry.COLUMN_START_LEVEL + " INTEGER NOT NULL," +
        DatabaseContract.ChargeSessionEntry.COLUMN_END_LEVEL + " INTEGER NOT NULL," +
        DatabaseContract.ChargeSessionEntry.COLUMN_START_CHARGE_COUNTER + " INTEGER," +
        DatabaseContract.ChargeSessionEntry.COLUMN_END_CHARGE_COUNTER + " INTEGER," +
        DatabaseContract.ChargeSessionEntry.COLUMN_MAX_TEMPERATURE + " INTEGER," +
        DatabaseContract.ChargeSessionEntry.COLUMN_MIN_TEMPERATURE + " INTEGER," +
        DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_DURATION + " INTEGER DEFAULT 0," +
        DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_CHARGE_COUNTER_DIFF + " INTEGER DEFAULT 0," +
        DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_DURATION + " INTEGER DEFAULT 0," +
        DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_CHARGE_COUNTER_DIFF + " INTEGER DEFAULT 0," +
        DatabaseContract.ChargeSessionEntry.COLUMN_ESTIMATED_CAPACITY + " INTEGER," +
        DatabaseContract.ChargeSessionEntry.COLUMN_CYCLE_COUNT + " INTEGER," +
        DatabaseContract.ChargeSessionEntry.COLUMN_IS_ONGOING + " INTEGER DEFAULT 0," +
        DatabaseContract.ChargeSessionEntry.COLUMN_COUNTER + " INTEGER DEFAULT 0" +
        ");";
    
    // 创建daily_stats表的SQL
    private static final String SQL_CREATE_DAILY_STATS_TABLE = 
        "CREATE TABLE " + DatabaseContract.DailyStatsEntry.TABLE_NAME + " (" +
        DatabaseContract.DailyStatsEntry.COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
        DatabaseContract.DailyStatsEntry.COLUMN_DATE + " TEXT NOT NULL UNIQUE," +
        DatabaseContract.DailyStatsEntry.COLUMN_SESSION_COUNT + " INTEGER NOT NULL," +
        DatabaseContract.DailyStatsEntry.COLUMN_TOTAL_LEVEL_CHANGE + " INTEGER NOT NULL," +
        DatabaseContract.DailyStatsEntry.COLUMN_TOTAL_CHARGE_COUNTER_DIFF + " INTEGER NOT NULL," +
        DatabaseContract.DailyStatsEntry.COLUMN_ESTIMATED_CAPACITY + " INTEGER," +
        DatabaseContract.DailyStatsEntry.COLUMN_CYCLE_COUNT + " INTEGER," +
        DatabaseContract.DailyStatsEntry.COLUMN_CAPACITY + " INTEGER," +
        DatabaseContract.DailyStatsEntry.COLUMN_MAX_LEVEL_CHANGE + " INTEGER" +
        ");";
    
    // 创建weekly_stats表的SQL
    private static final String SQL_CREATE_WEEKLY_STATS_TABLE = 
        "CREATE TABLE " + DatabaseContract.WeeklyStatsEntry.TABLE_NAME + " (" +
        DatabaseContract.WeeklyStatsEntry.COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
        DatabaseContract.WeeklyStatsEntry.COLUMN_WEEK_START + " TEXT NOT NULL UNIQUE," +
        DatabaseContract.WeeklyStatsEntry.COLUMN_SESSION_COUNT + " INTEGER NOT NULL," +
        DatabaseContract.WeeklyStatsEntry.COLUMN_TOTAL_LEVEL_CHANGE + " INTEGER NOT NULL," +
        DatabaseContract.WeeklyStatsEntry.COLUMN_TOTAL_CHARGE_COUNTER_DIFF + " INTEGER NOT NULL," +
        DatabaseContract.WeeklyStatsEntry.COLUMN_ESTIMATED_CAPACITY + " INTEGER," +
        DatabaseContract.WeeklyStatsEntry.COLUMN_CYCLE_COUNT + " INTEGER" +
        ");";
    
    // 创建monthly_stats表的SQL
    private static final String SQL_CREATE_MONTHLY_STATS_TABLE = 
        "CREATE TABLE " + DatabaseContract.MonthlyStatsEntry.TABLE_NAME + " (" +
        DatabaseContract.MonthlyStatsEntry.COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
        DatabaseContract.MonthlyStatsEntry.COLUMN_YEAR_MONTH + " TEXT NOT NULL UNIQUE," +
        DatabaseContract.MonthlyStatsEntry.COLUMN_SESSION_COUNT + " INTEGER NOT NULL," +
        DatabaseContract.MonthlyStatsEntry.COLUMN_TOTAL_LEVEL_CHANGE + " INTEGER NOT NULL," +
        DatabaseContract.MonthlyStatsEntry.COLUMN_TOTAL_CHARGE_COUNTER_DIFF + " INTEGER NOT NULL," +
        DatabaseContract.MonthlyStatsEntry.COLUMN_ESTIMATED_CAPACITY + " INTEGER," +
        DatabaseContract.MonthlyStatsEntry.COLUMN_CYCLE_COUNT + " INTEGER" +
        ");";
    
    // 创建索引
    private static final String SQL_CREATE_SESSION_TYPE_TIMESTAMP_INDEX = 
        "CREATE INDEX idx_session_type_timestamp ON " + 
        DatabaseContract.ChargeSessionEntry.TABLE_NAME + 
        "(" + DatabaseContract.ChargeSessionEntry.COLUMN_SESSION_TYPE + ", " +
        DatabaseContract.ChargeSessionEntry.COLUMN_START_TIMESTAMP + " DESC);";
    
    private static final String SQL_CREATE_DAILY_STATS_DATE_INDEX = 
        "CREATE INDEX idx_daily_stats_date ON " + 
        DatabaseContract.DailyStatsEntry.TABLE_NAME + 
        "(" + DatabaseContract.DailyStatsEntry.COLUMN_DATE + ");";
    
    private static final String SQL_CREATE_WEEKLY_STATS_WEEK_INDEX = 
        "CREATE INDEX idx_weekly_stats_week ON " + 
        DatabaseContract.WeeklyStatsEntry.TABLE_NAME + 
        "(" + DatabaseContract.WeeklyStatsEntry.COLUMN_WEEK_START + ");";
    
    private static final String SQL_CREATE_MONTHLY_STATS_MONTH_INDEX = 
        "CREATE INDEX idx_monthly_stats_month ON " + 
        DatabaseContract.MonthlyStatsEntry.TABLE_NAME + 
        "(" + DatabaseContract.MonthlyStatsEntry.COLUMN_YEAR_MONTH + ");";
    
    public BatteryDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * 升级到版本7：删除 weekly_stats 与 monthly_stats 及其索引
     */
    private void upgradeToVersion7(SQLiteDatabase db) {
        try {
            db.execSQL("DROP INDEX IF EXISTS idx_weekly_stats_week");
            db.execSQL("DROP INDEX IF EXISTS idx_monthly_stats_month");
            db.execSQL("DROP TABLE IF EXISTS " + DatabaseContract.WeeklyStatsEntry.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + DatabaseContract.MonthlyStatsEntry.TABLE_NAME);
            Log.i(TAG, "Successfully upgraded database to version 7 (drop weekly/monthly tables)");
        } catch (Exception e) {
            Log.e(TAG, "Failed to upgrade database to version 7: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * 升级到版本8：为 daily_stats 表添加 capacity 和 maxLevelChange 列
     */
    private void upgradeToVersion8(SQLiteDatabase db) {
        try {
            db.execSQL("ALTER TABLE " + DatabaseContract.DailyStatsEntry.TABLE_NAME + 
                       " ADD COLUMN " + DatabaseContract.DailyStatsEntry.COLUMN_CAPACITY + " INTEGER");
            db.execSQL("ALTER TABLE " + DatabaseContract.DailyStatsEntry.TABLE_NAME + 
                       " ADD COLUMN " + DatabaseContract.DailyStatsEntry.COLUMN_MAX_LEVEL_CHANGE + " INTEGER");
            Log.i(TAG, "Successfully upgraded database to version 8 (add capacity and maxLevelChange columns)");
        } catch (Exception e) {
            Log.e(TAG, "Failed to upgrade database to version 8: " + e.getMessage());
            throw e;
        }
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_CHARGE_SESSIONS_TABLE);
        db.execSQL(SQL_CREATE_DAILY_STATS_TABLE);
        db.execSQL(SQL_CREATE_SESSION_TYPE_TIMESTAMP_INDEX);
        db.execSQL(SQL_CREATE_DAILY_STATS_DATE_INDEX);
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 4) {
            // 完全重建，删除所有旧表
            db.execSQL("DROP TABLE IF EXISTS " + DatabaseContract.ChargeSessionEntry.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + DatabaseContract.DailyStatsEntry.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + DatabaseContract.WeeklyStatsEntry.TABLE_NAME);
            db.execSQL("DROP TABLE IF EXISTS " + DatabaseContract.MonthlyStatsEntry.TABLE_NAME);
            onCreate(db);
        } 
        if (oldVersion == 4 && newVersion >= 5) {
            // 版本5：删除 screen_on_level_change 列
            // 使用完全重建表的方式确保列被删除
            upgradeToVersion5(db);
        } 
        if (oldVersion == 5 && newVersion >= 6) {
            // 版本6：添加 counter 列
            upgradeToVersion6(db);
        }
        if (oldVersion <= 6 && newVersion >= 7) {
            // 版本7：删除 weekly/monthly 表与索引
            upgradeToVersion7(db);
        }
        if (oldVersion == 7 && newVersion >= 8) {
            // 版本8：添加 capacity 和 maxLevelChange 列
            upgradeToVersion8(db);
        }
    }
    
    /**
     * 升级到版本5：删除 screen_on_level_change 列
     * 通过重建表的方式实现，兼容所有SQLite版本
     */
    private void upgradeToVersion5(SQLiteDatabase db) {
        // 创建临时表（不包含 screen_on_level_change 列）
        String tempTableName = "charge_sessions_temp";
        String createTempTable = "CREATE TABLE " + tempTableName + " (" +
            DatabaseContract.ChargeSessionEntry.COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            DatabaseContract.ChargeSessionEntry.COLUMN_SESSION_TYPE + " INTEGER NOT NULL," +
            DatabaseContract.ChargeSessionEntry.COLUMN_START_TIMESTAMP + " INTEGER NOT NULL," +
            DatabaseContract.ChargeSessionEntry.COLUMN_END_TIMESTAMP + " INTEGER NOT NULL," +
            DatabaseContract.ChargeSessionEntry.COLUMN_PAUSE_TIMESTAMP + " INTEGER DEFAULT 0," +
            DatabaseContract.ChargeSessionEntry.COLUMN_START_LEVEL + " INTEGER NOT NULL," +
            DatabaseContract.ChargeSessionEntry.COLUMN_END_LEVEL + " INTEGER NOT NULL," +
            DatabaseContract.ChargeSessionEntry.COLUMN_START_CHARGE_COUNTER + " INTEGER," +
            DatabaseContract.ChargeSessionEntry.COLUMN_END_CHARGE_COUNTER + " INTEGER," +
            DatabaseContract.ChargeSessionEntry.COLUMN_MAX_TEMPERATURE + " INTEGER," +
            DatabaseContract.ChargeSessionEntry.COLUMN_MIN_TEMPERATURE + " INTEGER," +
            DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_DURATION + " INTEGER DEFAULT 0," +
            DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_CHARGE_COUNTER_DIFF + " INTEGER DEFAULT 0," +
            DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_DURATION + " INTEGER DEFAULT 0," +
            DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_CHARGE_COUNTER_DIFF + " INTEGER DEFAULT 0," +
            DatabaseContract.ChargeSessionEntry.COLUMN_ESTIMATED_CAPACITY + " INTEGER," +
            DatabaseContract.ChargeSessionEntry.COLUMN_CYCLE_COUNT + " INTEGER," +
            DatabaseContract.ChargeSessionEntry.COLUMN_IS_ONGOING + " INTEGER DEFAULT 0" +
            ");";
        
        // 复制数据到临时表（跳过 screen_on_level_change 列）
        String copyData = "INSERT INTO " + tempTableName + " (" +
            DatabaseContract.ChargeSessionEntry.COLUMN_ID + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_SESSION_TYPE + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_START_TIMESTAMP + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_END_TIMESTAMP + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_PAUSE_TIMESTAMP + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_START_LEVEL + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_END_LEVEL + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_START_CHARGE_COUNTER + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_END_CHARGE_COUNTER + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_MAX_TEMPERATURE + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_MIN_TEMPERATURE + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_DURATION + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_CHARGE_COUNTER_DIFF + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_DURATION + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_CHARGE_COUNTER_DIFF + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_ESTIMATED_CAPACITY + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_CYCLE_COUNT + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_IS_ONGOING +
            ") SELECT " +
            DatabaseContract.ChargeSessionEntry.COLUMN_ID + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_SESSION_TYPE + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_START_TIMESTAMP + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_END_TIMESTAMP + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_PAUSE_TIMESTAMP + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_START_LEVEL + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_END_LEVEL + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_START_CHARGE_COUNTER + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_END_CHARGE_COUNTER + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_MAX_TEMPERATURE + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_MIN_TEMPERATURE + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_DURATION + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_CHARGE_COUNTER_DIFF + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_DURATION + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_CHARGE_COUNTER_DIFF + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_ESTIMATED_CAPACITY + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_CYCLE_COUNT + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_IS_ONGOING +
            " FROM " + DatabaseContract.ChargeSessionEntry.TABLE_NAME;
        
        // 删除旧表
        String dropOldTable = "DROP TABLE " + DatabaseContract.ChargeSessionEntry.TABLE_NAME;
        
        // 重命名临时表
        String renameTable = "ALTER TABLE " + tempTableName + " RENAME TO " + DatabaseContract.ChargeSessionEntry.TABLE_NAME;
        
        // 执行迁移
        try {
            db.execSQL(createTempTable);
            db.execSQL(copyData);
            db.execSQL(dropOldTable);
            db.execSQL(renameTable);
            Log.i(TAG, "Successfully upgraded database to version 5");
        } catch (Exception e) {
            Log.e(TAG, "Failed to upgrade database to version 5: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * 升级到版本6：添加 counter 列
     * 通过重建表的方式实现，兼容所有SQLite版本
     */
    private void upgradeToVersion6(SQLiteDatabase db) {
        // 创建临时表（包含 counter 列）
        String tempTableName = "charge_sessions_temp";
        String createTempTable = "CREATE TABLE " + tempTableName + " (" +
            DatabaseContract.ChargeSessionEntry.COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
            DatabaseContract.ChargeSessionEntry.COLUMN_SESSION_TYPE + " INTEGER NOT NULL," +
            DatabaseContract.ChargeSessionEntry.COLUMN_START_TIMESTAMP + " INTEGER NOT NULL," +
            DatabaseContract.ChargeSessionEntry.COLUMN_END_TIMESTAMP + " INTEGER NOT NULL," +
            DatabaseContract.ChargeSessionEntry.COLUMN_PAUSE_TIMESTAMP + " INTEGER DEFAULT 0," +
            DatabaseContract.ChargeSessionEntry.COLUMN_START_LEVEL + " INTEGER NOT NULL," +
            DatabaseContract.ChargeSessionEntry.COLUMN_END_LEVEL + " INTEGER NOT NULL," +
            DatabaseContract.ChargeSessionEntry.COLUMN_START_CHARGE_COUNTER + " INTEGER," +
            DatabaseContract.ChargeSessionEntry.COLUMN_END_CHARGE_COUNTER + " INTEGER," +
            DatabaseContract.ChargeSessionEntry.COLUMN_MAX_TEMPERATURE + " INTEGER," +
            DatabaseContract.ChargeSessionEntry.COLUMN_MIN_TEMPERATURE + " INTEGER," +
            DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_DURATION + " INTEGER DEFAULT 0," +
            DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_CHARGE_COUNTER_DIFF + " INTEGER DEFAULT 0," +
            DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_DURATION + " INTEGER DEFAULT 0," +
            DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_CHARGE_COUNTER_DIFF + " INTEGER DEFAULT 0," +
            DatabaseContract.ChargeSessionEntry.COLUMN_ESTIMATED_CAPACITY + " INTEGER," +
            DatabaseContract.ChargeSessionEntry.COLUMN_CYCLE_COUNT + " INTEGER," +
            DatabaseContract.ChargeSessionEntry.COLUMN_IS_ONGOING + " INTEGER DEFAULT 0," +
            DatabaseContract.ChargeSessionEntry.COLUMN_COUNTER + " INTEGER DEFAULT 0" +
            ");";
        
        // 复制数据到临时表（counter 列默认为 0）
        String copyData = "INSERT INTO " + tempTableName + " (" +
            DatabaseContract.ChargeSessionEntry.COLUMN_ID + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_SESSION_TYPE + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_START_TIMESTAMP + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_END_TIMESTAMP + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_PAUSE_TIMESTAMP + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_START_LEVEL + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_END_LEVEL + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_START_CHARGE_COUNTER + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_END_CHARGE_COUNTER + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_MAX_TEMPERATURE + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_MIN_TEMPERATURE + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_DURATION + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_CHARGE_COUNTER_DIFF + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_DURATION + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_CHARGE_COUNTER_DIFF + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_ESTIMATED_CAPACITY + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_CYCLE_COUNT + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_IS_ONGOING + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_COUNTER +
            ") SELECT " +
            DatabaseContract.ChargeSessionEntry.COLUMN_ID + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_SESSION_TYPE + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_START_TIMESTAMP + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_END_TIMESTAMP + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_PAUSE_TIMESTAMP + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_START_LEVEL + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_END_LEVEL + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_START_CHARGE_COUNTER + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_END_CHARGE_COUNTER + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_MAX_TEMPERATURE + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_MIN_TEMPERATURE + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_DURATION + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_CHARGE_COUNTER_DIFF + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_DURATION + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_CHARGE_COUNTER_DIFF + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_ESTIMATED_CAPACITY + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_CYCLE_COUNT + "," +
            DatabaseContract.ChargeSessionEntry.COLUMN_IS_ONGOING + "," +
            "0" +
            " FROM " + DatabaseContract.ChargeSessionEntry.TABLE_NAME;
        
        // 删除旧表
        String dropOldTable = "DROP TABLE " + DatabaseContract.ChargeSessionEntry.TABLE_NAME;
        
        // 重命名临时表
        String renameTable = "ALTER TABLE " + tempTableName + " RENAME TO " + DatabaseContract.ChargeSessionEntry.TABLE_NAME;
        
        // 执行迁移
        try {
            db.execSQL(createTempTable);
            db.execSQL(copyData);
            db.execSQL(dropOldTable);
            db.execSQL(renameTable);
            Log.i(TAG, "Successfully upgraded database to version 6");
        } catch (Exception e) {
            Log.e(TAG, "Failed to upgrade database to version 6: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * 将ChargeSession转换为ContentValues
     */
    private ContentValues sessionToContentValues(ChargeSession session) {
        ContentValues values = new ContentValues();
        
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_SESSION_TYPE, session.getSessionType());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_START_TIMESTAMP, session.getStartTimestamp());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_END_TIMESTAMP, session.getEndTimestamp());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_PAUSE_TIMESTAMP, session.getPauseTimestamp());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_START_LEVEL, session.getStartLevel());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_END_LEVEL, session.getEndLevel());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_START_CHARGE_COUNTER, session.getStartChargeCounter());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_END_CHARGE_COUNTER, session.getEndChargeCounter());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_MAX_TEMPERATURE, session.getMaxTemperature());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_MIN_TEMPERATURE, session.getMinTemperature());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_DURATION, session.getScreenOnDuration());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_CHARGE_COUNTER_DIFF, session.getScreenOnChargeCounterDiff());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_DURATION, session.getDozeDuration());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_CHARGE_COUNTER_DIFF, session.getDozeChargeCounterDiff());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_ESTIMATED_CAPACITY, session.getEstimatedCapacity());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_CYCLE_COUNT, session.getCycleCount());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_IS_ONGOING, session.isOngoing() ? 1 : 0);
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_COUNTER, session.getCounter());

        return values;
    }
    
    /**
     * 从Cursor解析ChargeSession
     */
    private ChargeSession parseSessionFromCursor(Cursor cursor) {
        ChargeSession session = new ChargeSession();
        
        session.setId(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_ID)));
        session.setSessionType(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_SESSION_TYPE)));
        session.setStartTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_START_TIMESTAMP)));
        session.setEndTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_END_TIMESTAMP)));
        session.setPauseTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_PAUSE_TIMESTAMP)));
        session.setStartLevel(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_START_LEVEL)));
        session.setEndLevel(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_END_LEVEL)));
        session.setStartChargeCounter(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_START_CHARGE_COUNTER)));
        session.setEndChargeCounter(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_END_CHARGE_COUNTER)));
        session.setMaxTemperature(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_MAX_TEMPERATURE)));
        session.setMinTemperature(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_MIN_TEMPERATURE)));
        session.setScreenOnDuration(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_DURATION)));
        session.setScreenOnChargeCounterDiff(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_CHARGE_COUNTER_DIFF)));
        session.setDozeDuration(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_DURATION)));
        session.setDozeChargeCounterDiff(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_CHARGE_COUNTER_DIFF)));
        session.setEstimatedCapacity(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_ESTIMATED_CAPACITY)));
        session.setCycleCount(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_CYCLE_COUNT)));
        session.setOngoing(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_IS_ONGOING)) == 1);
        session.setCounter(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_COUNTER)));

        return session;
    }
    
    /**
     * 分页查询充放电阶段
     */
    public List<ChargeSession> getSessions(int offset, int limit) {
        SQLiteDatabase db = getReadableDatabase();
        List<ChargeSession> sessions = new ArrayList<>();

        String query = "SELECT * FROM " + DatabaseContract.ChargeSessionEntry.TABLE_NAME +
                      " ORDER BY " + DatabaseContract.ChargeSessionEntry.COLUMN_START_TIMESTAMP + " DESC" +
                      " LIMIT ? OFFSET ?";

        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(limit), String.valueOf(offset)});

        while (cursor.moveToNext()) {
            ChargeSession session = parseSessionFromCursor(cursor);
            sessions.add(session);
        }
        cursor.close();

        return sessions;
    }

    /**
     * 获取进行中的会话
     */
    public List<ChargeSession> getOngoingSessions() {
        SQLiteDatabase db = getReadableDatabase();
        List<ChargeSession> sessions = new ArrayList<>();

        String query = "SELECT * FROM " + DatabaseContract.ChargeSessionEntry.TABLE_NAME +
                      " WHERE " + DatabaseContract.ChargeSessionEntry.COLUMN_IS_ONGOING + " = 1" +
                      " ORDER BY " + DatabaseContract.ChargeSessionEntry.COLUMN_START_TIMESTAMP + " DESC";

        Cursor cursor = db.rawQuery(query, null);

        while (cursor.moveToNext()) {
            ChargeSession session = parseSessionFromCursor(cursor);
            sessions.add(session);
        }
        cursor.close();

        return sessions;
    }

    /**
     * 部分更新会话（用于进行中会话的定期更新）
     * 不改变 is_ongoing 状态，不触发统计更新
     */
    public void updateSessionPartial(ChargeSession session) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_PAUSE_TIMESTAMP, session.getPauseTimestamp());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_END_TIMESTAMP, session.getEndTimestamp());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_END_LEVEL, session.getEndLevel());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_END_CHARGE_COUNTER, session.getEndChargeCounter());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_MAX_TEMPERATURE, session.getMaxTemperature());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_MIN_TEMPERATURE, session.getMinTemperature());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_DURATION, session.getScreenOnDuration());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_CHARGE_COUNTER_DIFF, session.getScreenOnChargeCounterDiff());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_DURATION, session.getDozeDuration());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_CHARGE_COUNTER_DIFF, session.getDozeChargeCounterDiff());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_ESTIMATED_CAPACITY, session.getEstimatedCapacity());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_CYCLE_COUNT, session.getCycleCount());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_COUNTER, session.getCounter());

        db.update(DatabaseContract.ChargeSessionEntry.TABLE_NAME, values,
                 DatabaseContract.ChargeSessionEntry.COLUMN_ID + " = ?",
                 new String[]{String.valueOf(session.getId())});
    }

    /**
     * 完整更新会话并完成它（设置 is_ongoing = false，触发统计更新）
     */
    public void finishSession(ChargeSession session) {
        SQLiteDatabase db = getWritableDatabase();

        // 更新会话记录
        ContentValues values = sessionToContentValues(session);
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_IS_ONGOING, 0);
        db.update(DatabaseContract.ChargeSessionEntry.TABLE_NAME, values,
                 DatabaseContract.ChargeSessionEntry.COLUMN_ID + " = ?",
                 new String[]{String.valueOf(session.getId())});
        // 统计改为随样本流即时累计，这里不再更新聚合数据
    }

    /**
     * 插入新的进行中会话
     */
    public long insertOngoingSession(ChargeSession session) {
        session.setOngoing(true);
        ContentValues values = sessionToContentValues(session);
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_IS_ONGOING, 1);
        return getWritableDatabase().insert(DatabaseContract.ChargeSessionEntry.TABLE_NAME, null, values);
    }
    
    /**
     * 获取总记录数
     */
    public int getSessionCount() {
        SQLiteDatabase db = getReadableDatabase();
        String query = "SELECT COUNT(*) FROM " + DatabaseContract.ChargeSessionEntry.TABLE_NAME;
        Cursor cursor = db.rawQuery(query, null);
        
        int count = 0;
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }
        cursor.close();
        
        return count;
    }

    /**
     * 查询 daily_stats 中指定日期范围内的 estimated_capacity
     * @param startDate 包含的开始日期，格式 yyyy-MM-dd；为 null 表示不限制下界
     * @param endDate   包含的结束日期，格式 yyyy-MM-dd；为 null 表示不限制上界
     * @return 日期->容量 的有序映射（按日期升序）
     */
    public LinkedHashMap<String, Integer> getDailyEstimatedCapacities(String startDate, String endDate) {
        SQLiteDatabase db = getReadableDatabase();
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();

        StringBuilder sb = new StringBuilder();
        List<String> args = new ArrayList<>();

        sb.append("SELECT ")
          .append(DatabaseContract.DailyStatsEntry.COLUMN_DATE)
          .append(", ")
          .append(DatabaseContract.DailyStatsEntry.COLUMN_TOTAL_CHARGE_COUNTER_DIFF)
          .append(" FROM ")
          .append(DatabaseContract.DailyStatsEntry.TABLE_NAME)
          .append(" WHERE ")
          .append(DatabaseContract.DailyStatsEntry.COLUMN_TOTAL_CHARGE_COUNTER_DIFF)
          .append(" IS NOT NULL AND ")
          .append(DatabaseContract.DailyStatsEntry.COLUMN_TOTAL_CHARGE_COUNTER_DIFF)
          .append(" > 0");

        if (startDate != null) {
            sb.append(" AND ")
              .append(DatabaseContract.DailyStatsEntry.COLUMN_DATE)
              .append(" >= ?");
            args.add(startDate);
        }
        if (endDate != null) {
            sb.append(" AND ")
              .append(DatabaseContract.DailyStatsEntry.COLUMN_DATE)
              .append(" <= ?");
            args.add(endDate);
        }

        sb.append(" ORDER BY ")
          .append(DatabaseContract.DailyStatsEntry.COLUMN_DATE)
          .append(" ASC");

        Cursor cursor = db.rawQuery(sb.toString(), args.toArray(new String[0]));
        while (cursor.moveToNext()) {
            String date = cursor.getString(0);
            int cap = cursor.getInt(1);
            result.put(date, cap);
        }
        cursor.close();

        return result;
    }
    
    /**
     * 格式化日期
     */
    private String formatDate(long timestamp, String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
        return sdf.format(timestamp);
    }

    /**
     * 获取指定日期的daily_stats，如果不存在则新建
     * @param date 日期字符串，格式 yyyy-MM-dd
     * @return DailyStats对象
     */
    public DailyStats getDailyStats(String date) {
        if (!isValidDateFormat(date)) {
            return null;
        }
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(DatabaseContract.DailyStatsEntry.TABLE_NAME, null,
                DatabaseContract.DailyStatsEntry.COLUMN_DATE + " = ?",
                new String[]{date}, null, null, null);
        DailyStats stats = new DailyStats();
        if (cursor.moveToFirst()) {
            stats.setDate(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.DailyStatsEntry.COLUMN_DATE)));
            stats.setSessionCount(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyStatsEntry.COLUMN_SESSION_COUNT)));
            stats.setTotalLevelChange(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyStatsEntry.COLUMN_TOTAL_LEVEL_CHANGE)));
            stats.setTotalChargeCounterDiff(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyStatsEntry.COLUMN_TOTAL_CHARGE_COUNTER_DIFF)));
            stats.setEstimatedCapacity(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyStatsEntry.COLUMN_ESTIMATED_CAPACITY)));
            stats.setCycleCount(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyStatsEntry.COLUMN_CYCLE_COUNT)));
            stats.setCapacity(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyStatsEntry.COLUMN_CAPACITY)));
            stats.setMaxLevelChange(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyStatsEntry.COLUMN_MAX_LEVEL_CHANGE)));
        } else {
            stats.setDate(date);
            stats.setSessionCount(0);
            stats.setTotalLevelChange(0);
            stats.setTotalChargeCounterDiff(0);
            stats.setEstimatedCapacity(0);
            stats.setCycleCount(0);
            stats.setCapacity(0);
            stats.setMaxLevelChange(0);
        }
        cursor.close();
        return stats;
    }

    /**
     * 传入一个DailyStats，更新对应日期的数据(覆盖)，如不存在则新建
     * @param stats DailyStats对象
     * @return 是否成功
     */
    public boolean updateDailyStats(DailyStats stats) {
        if (stats == null) {
            return false;
        }
        String date = stats.getDate();
        if (!isValidDateFormat(date)) {
            return false;
        }
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseContract.DailyStatsEntry.COLUMN_DATE, date);
        values.put(DatabaseContract.DailyStatsEntry.COLUMN_SESSION_COUNT, stats.getSessionCount());
        values.put(DatabaseContract.DailyStatsEntry.COLUMN_TOTAL_LEVEL_CHANGE, stats.getTotalLevelChange());
        values.put(DatabaseContract.DailyStatsEntry.COLUMN_TOTAL_CHARGE_COUNTER_DIFF, stats.getTotalChargeCounterDiff());
        values.put(DatabaseContract.DailyStatsEntry.COLUMN_ESTIMATED_CAPACITY, stats.getEstimatedCapacity());
        values.put(DatabaseContract.DailyStatsEntry.COLUMN_CYCLE_COUNT, stats.getCycleCount());
        values.put(DatabaseContract.DailyStatsEntry.COLUMN_CAPACITY, stats.getCapacity());
        values.put(DatabaseContract.DailyStatsEntry.COLUMN_MAX_LEVEL_CHANGE, stats.getMaxLevelChange());
        int rowsAffected = db.update(DatabaseContract.DailyStatsEntry.TABLE_NAME, values,
                DatabaseContract.DailyStatsEntry.COLUMN_DATE + " = ?",
                new String[]{date});
        if (rowsAffected == 0) {
            long result = db.insert(DatabaseContract.DailyStatsEntry.TABLE_NAME, null, values);
            return result != -1;
        }
        return true;
    }

    /**
     * 验证日期格式是否为 yyyy-MM-dd
     * @param date 日期字符串
     * @return 是否有效
     */
    private boolean isValidDateFormat(String date) {
        if (date == null || date.length() != 10) {
            return false;
        }
        return date.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    //TODO 在缓存中做增量更新
    /**
     * 基于样本对增量，按当前样本日期（本地时区）更新 daily_stats。
     * isCharging=false 时不累计增量，仅覆盖估计容量与周期数。
     * isFirstChargeUpdate=true 时对当日 session_count +1。
     */
    public void applySampleDeltaToDaily(long tPrev, long tNow,
                                        int ccPrev, int ccNow,
                                        int levelPrev, int levelNow,
                                        boolean isCharging,
                                        int estimatedCapacity, int cycleCount,
                                        boolean isFirstChargeUpdate) {
        String date = formatDate(tNow, "yyyy-MM-dd");
        SQLiteDatabase db = getWritableDatabase();
        Cursor cursor = db.query(DatabaseContract.DailyStatsEntry.TABLE_NAME, null,
                DatabaseContract.DailyStatsEntry.COLUMN_DATE + " = ?",
                new String[]{date}, null, null, null);
        boolean exists = cursor.moveToFirst();
        int currentSessionCount = 0;
        int currentLevelChange = 0;
        int currentChargeDiff = 0;
        if (exists) {
            currentSessionCount = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyStatsEntry.COLUMN_SESSION_COUNT));
            currentLevelChange = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyStatsEntry.COLUMN_TOTAL_LEVEL_CHANGE));
            currentChargeDiff = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyStatsEntry.COLUMN_TOTAL_CHARGE_COUNTER_DIFF));
        }
        cursor.close();

        int deltaLevel = (levelPrev >= 0 && levelNow >= 0) ? (levelNow - levelPrev) : 0;
        int deltaCharge = (ccPrev >= 0 && ccNow >= 0) ? (ccNow - ccPrev) : 0;

        ContentValues values = new ContentValues();
        if (exists) {
            if (isCharging) {
                values.put(DatabaseContract.DailyStatsEntry.COLUMN_TOTAL_LEVEL_CHANGE, currentLevelChange + deltaLevel);
                values.put(DatabaseContract.DailyStatsEntry.COLUMN_TOTAL_CHARGE_COUNTER_DIFF, currentChargeDiff + deltaCharge);
            }
            if (isFirstChargeUpdate) {
                values.put(DatabaseContract.DailyStatsEntry.COLUMN_SESSION_COUNT, currentSessionCount + 1);
            }
            values.put(DatabaseContract.DailyStatsEntry.COLUMN_ESTIMATED_CAPACITY, estimatedCapacity);
            values.put(DatabaseContract.DailyStatsEntry.COLUMN_CYCLE_COUNT, cycleCount);
            if (values.size() > 0) {
                db.update(DatabaseContract.DailyStatsEntry.TABLE_NAME, values,
                        DatabaseContract.DailyStatsEntry.COLUMN_DATE + " = ?",
                        new String[]{date});
            }
        } else {
            values.put(DatabaseContract.DailyStatsEntry.COLUMN_DATE, date);
            values.put(DatabaseContract.DailyStatsEntry.COLUMN_SESSION_COUNT, isFirstChargeUpdate ? 1 : 0);
            values.put(DatabaseContract.DailyStatsEntry.COLUMN_TOTAL_LEVEL_CHANGE, isCharging ? deltaLevel : 0);
            values.put(DatabaseContract.DailyStatsEntry.COLUMN_TOTAL_CHARGE_COUNTER_DIFF, isCharging ? deltaCharge : 0);
            values.put(DatabaseContract.DailyStatsEntry.COLUMN_ESTIMATED_CAPACITY, estimatedCapacity);
            values.put(DatabaseContract.DailyStatsEntry.COLUMN_CYCLE_COUNT, cycleCount);
            db.insert(DatabaseContract.DailyStatsEntry.TABLE_NAME, null, values);
        }
    }
    
    //TODO remove
    /**
     * 获取周初日期（周日）
     */
    private String getWeekStart(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
        return formatDate(cal.getTimeInMillis(), "yyyy-MM-dd");
    }
    
    /**
     * 获取每日统计数据
     */
    public List<DailyStats> getDailyStats(int offset, int limit) {
        SQLiteDatabase db = getReadableDatabase();
        List<DailyStats> stats = new ArrayList<>();
        
        String query = "SELECT * FROM " + DatabaseContract.DailyStatsEntry.TABLE_NAME +
                      " ORDER BY " + DatabaseContract.DailyStatsEntry.COLUMN_DATE + " DESC" +
                      " LIMIT ? OFFSET ?";
        
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(limit), String.valueOf(offset)});
        
        while (cursor.moveToNext()) {
            DailyStats stat = new DailyStats();
            stat.setDate(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.DailyStatsEntry.COLUMN_DATE)));
            stat.setSessionCount(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyStatsEntry.COLUMN_SESSION_COUNT)));
            stat.setTotalLevelChange(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyStatsEntry.COLUMN_TOTAL_LEVEL_CHANGE)));
            stat.setTotalChargeCounterDiff(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyStatsEntry.COLUMN_TOTAL_CHARGE_COUNTER_DIFF)));
            stat.setEstimatedCapacity(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyStatsEntry.COLUMN_ESTIMATED_CAPACITY)));
            stat.setCycleCount(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyStatsEntry.COLUMN_CYCLE_COUNT)));
            stats.add(stat);
        }
        cursor.close();
        
        return stats;
    }
    
    /**
     * 获取每周统计数据
     */
    public List<DailyStats> getWeeklyStats(int offset, int limit) {
        List<DailyStats> recent = getDailyStats(offset, (limit+1)*7);
        List<DailyStats> result = new ArrayList<>();
        Map<String, DailyStats> map = new LinkedHashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat weekSdf = new SimpleDateFormat("YYww", Locale.getDefault());
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.SUNDAY);
        for (DailyStats ds : recent) {
            try {
                cal.setTime(sdf.parse(ds.getDate()));
            } catch (Exception e) { continue; }
            cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
            String weekStart = weekSdf.format(cal.getTime());
            DailyStats ws = map.get(weekStart);
            if (ws == null) {
                ws = new DailyStats();
                ws.setDate(weekStart);
                map.put(weekStart, ws);
            }
            ws.merge(ds);
        }
        List<DailyStats> all = new ArrayList<>(map.values());
        int start = Math.min(offset, all.size());
        int end = Math.min(start + limit, all.size());
        for (int i = start; i < end; i++) {
            result.add(all.get(i));
        }
        return result;
    }
    
    /**
     * 获取每月统计数据
     */
    public List<DailyStats> getMonthlyStats(int offset, int limit) {
        List<DailyStats> recent = getDailyStats(offset, (limit+1)*31);
        List<DailyStats> result = new ArrayList<>();
        Map<String, DailyStats> map = new LinkedHashMap<>();
        for (DailyStats ds : recent) {
            String ym = ds.getDate().substring(0, 7); // YYYY-MM
            DailyStats ms = map.get(ym);
            if (ms == null) {
                ms = new DailyStats();
                ms.setDate(ym);
                map.put(ym, ms);
            }
            ms.merge(ds);
        }
        List<DailyStats> all = new ArrayList<>(map.values());
        int start = Math.min(offset, all.size());
        int end = Math.min(start + limit, all.size());
        for (int i = start; i < end; i++) {
            result.add(all.get(i));
        }
        return result;
    }
}

