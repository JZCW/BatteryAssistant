package com.upo.batteryassistant.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.upo.batteryassistant.data.ChargeSession;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 电池数据库Helper
 */
public class BatteryDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "BatteryDatabaseHelper";
    private static final String DATABASE_NAME = "battery_assistant.db";
    private static final int DATABASE_VERSION = 4;
    
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
        DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_LEVEL_CHANGE + " INTEGER DEFAULT 0," +
        DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_CHARGE_COUNTER_DIFF + " INTEGER DEFAULT 0," +
        DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_DURATION + " INTEGER DEFAULT 0," +
        DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_CHARGE_COUNTER_DIFF + " INTEGER DEFAULT 0," +
        DatabaseContract.ChargeSessionEntry.COLUMN_ESTIMATED_CAPACITY + " INTEGER," +
        DatabaseContract.ChargeSessionEntry.COLUMN_CYCLE_COUNT + " INTEGER," +
        DatabaseContract.ChargeSessionEntry.COLUMN_IS_ONGOING + " INTEGER DEFAULT 0" +
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
        DatabaseContract.DailyStatsEntry.COLUMN_CYCLE_COUNT + " INTEGER" +
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
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_CHARGE_SESSIONS_TABLE);
        db.execSQL(SQL_CREATE_DAILY_STATS_TABLE);
        db.execSQL(SQL_CREATE_WEEKLY_STATS_TABLE);
        db.execSQL(SQL_CREATE_MONTHLY_STATS_TABLE);
        db.execSQL(SQL_CREATE_SESSION_TYPE_TIMESTAMP_INDEX);
        db.execSQL(SQL_CREATE_DAILY_STATS_DATE_INDEX);
        db.execSQL(SQL_CREATE_WEEKLY_STATS_WEEK_INDEX);
        db.execSQL(SQL_CREATE_MONTHLY_STATS_MONTH_INDEX);
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
    }
    
    /**
     * 插入充放电阶段（带合并逻辑）
     */
    public long insertSession(ChargeSession session) {
        SQLiteDatabase db = getWritableDatabase();
        
        // 尝试合并
        ChargeSession mergedSession = tryMergeSession(db, session);
        
        ContentValues values = sessionToContentValues(mergedSession);
        long id = db.insert(DatabaseContract.ChargeSessionEntry.TABLE_NAME, null, values);
        
        // 更新聚合数据（仅充电阶段）
        if (mergedSession.getSessionType() == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE) {
            updateDailyStats(db, mergedSession);
            updateWeeklyStats(db, mergedSession);
            updateMonthlyStats(db, mergedSession);
        }
        
        return id;
    }
    
    /**
     * 尝试合并会话
     */
    private ChargeSession tryMergeSession(SQLiteDatabase db, ChargeSession newSession) {
        // 获取最近的一个会话
        String query = "SELECT * FROM " + DatabaseContract.ChargeSessionEntry.TABLE_NAME +
                      " ORDER BY " + DatabaseContract.ChargeSessionEntry.COLUMN_END_TIMESTAMP + " DESC LIMIT 2";
        
        Cursor cursor = db.rawQuery(query, null);
        List<ChargeSession> recentSessions = new ArrayList<>();
        
        while (cursor.moveToNext()) {
            recentSessions.add(parseSessionFromCursor(cursor));
        }
        cursor.close();
        
        if (recentSessions.isEmpty()) {
            return newSession;
        }
        
        ChargeSession lastSession = recentSessions.get(0);
        
        // 检查是否可以合并
        if (canMergeSessions(lastSession, newSession)) {
            // 删除旧会话
            db.delete(DatabaseContract.ChargeSessionEntry.TABLE_NAME,
                     DatabaseContract.ChargeSessionEntry.COLUMN_ID + " = ?",
                     new String[]{String.valueOf(lastSession.getId())});
            
            // 合并到新会话
            lastSession.mergeWith(newSession);
            return lastSession;
        }
        
        // 检查不同类型合并（充-放-充）
        if (recentSessions.size() >= 2) {
            ChargeSession secondLastSession = recentSessions.get(1);
            if (canMergeDifferentType(secondLastSession, lastSession, newSession)) {
                // 删除中间和最后的会话
                db.delete(DatabaseContract.ChargeSessionEntry.TABLE_NAME,
                         DatabaseContract.ChargeSessionEntry.COLUMN_ID + " IN (?, ?)",
                         new String[]{String.valueOf(secondLastSession.getId()), 
                                     String.valueOf(lastSession.getId())});
                
                // 合并
                secondLastSession.mergeWith(newSession);
                return secondLastSession;
            }
        }
        
        return newSession;
    }
    
    /**
     * 检查同类型会话是否可以合并
     */
    private boolean canMergeSessions(ChargeSession earlier, ChargeSession later) {
        // 必须是同类型
        if (earlier.getSessionType() != later.getSessionType()) {
            return false;
        }
        
        // 检查时间间隔
        long interval = later.getStartTimestamp() - earlier.getEndTimestamp();
        if (interval >= DatabaseContract.MERGE_SAME_TYPE_INTERVAL_THRESHOLD) {
            return false;
        }
        
        // 检查电量差
        int levelDiff = Math.abs(later.getStartLevel() - earlier.getEndLevel());
        if (levelDiff > DatabaseContract.MERGE_SAME_TYPE_LEVEL_DIFF_THRESHOLD) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 检查不同类型会话是否可以合并（充-放-充）
     */
    private boolean canMergeDifferentType(ChargeSession first, ChargeSession middle, ChargeSession last) {
        // 第一和最后必须是同类型（充电）
        if (first.getSessionType() != last.getSessionType()) {
            return false;
        }
        if (first.getSessionType() != DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE) {
            return false;
        }
        
        // 中间必须是不同类型
        if (middle.getSessionType() == first.getSessionType()) {
            return false;
        }
        
        // 中间阶段持续时间必须小于阈值
        long duration = middle.getEndTimestamp() - middle.getStartTimestamp();
        if (duration >= DatabaseContract.MERGE_DIFFERENT_TYPE_THRESHOLD) {
            return false;
        }
        
        return true;
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
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_LEVEL_CHANGE, session.getScreenOnLevelChange());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_CHARGE_COUNTER_DIFF, session.getScreenOnChargeCounterDiff());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_DURATION, session.getDozeDuration());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_CHARGE_COUNTER_DIFF, session.getDozeChargeCounterDiff());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_ESTIMATED_CAPACITY, session.getEstimatedCapacity());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_CYCLE_COUNT, session.getCycleCount());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_IS_ONGOING, session.isOngoing() ? 1 : 0);

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
        session.setScreenOnLevelChange(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_LEVEL_CHANGE)));
        session.setScreenOnChargeCounterDiff(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_CHARGE_COUNTER_DIFF)));
        session.setDozeDuration(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_DURATION)));
        session.setDozeChargeCounterDiff(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_CHARGE_COUNTER_DIFF)));
        session.setEstimatedCapacity(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_ESTIMATED_CAPACITY)));
        session.setCycleCount(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_CYCLE_COUNT)));
        session.setOngoing(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_IS_ONGOING)) == 1);

        return session;
    }
    
    /**
     * 分页查询充放电阶段
     */
    public List<ChargeSession> getSessions(int offset, int limit) {
        SQLiteDatabase db = getReadableDatabase();
        List<ChargeSession> sessions = new ArrayList<>();

        // 首页：把进行中会话置顶（若存在），再补足已完成会话
        if (offset == 0) {
            // 取最新的进行中会话（通常最多1条）
            String ongoingQuery = "SELECT * FROM " + DatabaseContract.ChargeSessionEntry.TABLE_NAME +
                                 " WHERE " + DatabaseContract.ChargeSessionEntry.COLUMN_IS_ONGOING + " = 1" +
                                 " ORDER BY " + DatabaseContract.ChargeSessionEntry.COLUMN_START_TIMESTAMP + " DESC" +
                                 " LIMIT 1";
            Cursor ongoingCursor = db.rawQuery(ongoingQuery, null);
            int ongoingCount = 0;
            while (ongoingCursor.moveToNext()) {
                ChargeSession session = parseSessionFromCursor(ongoingCursor);
                sessions.add(session);
                ongoingCount++;
            }
            ongoingCursor.close();

            int finishedLimit = limit; // 始终取满已完成会话，允许首页总条数为 limit + ongoingCount
            if (finishedLimit > 0) {
                String finishedQuery = "SELECT * FROM " + DatabaseContract.ChargeSessionEntry.TABLE_NAME +
                                       " WHERE " + DatabaseContract.ChargeSessionEntry.COLUMN_IS_ONGOING + " = 0" +
                                       " ORDER BY " + DatabaseContract.ChargeSessionEntry.COLUMN_START_TIMESTAMP + " DESC" +
                                       " LIMIT ? OFFSET ?";
                Cursor finishedCursor = db.rawQuery(finishedQuery,
                        new String[]{String.valueOf(finishedLimit), String.valueOf(0)});
                while (finishedCursor.moveToNext()) {
                    ChargeSession session = parseSessionFromCursor(finishedCursor);
                    sessions.add(session);
                }
                finishedCursor.close();
            }
            return sessions;
        }

        // 后续分页：仅返回已完成会话
        String query = "SELECT * FROM " + DatabaseContract.ChargeSessionEntry.TABLE_NAME +
                      " WHERE " + DatabaseContract.ChargeSessionEntry.COLUMN_IS_ONGOING + " = 0" +
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
     * 删除进行中的会话
     */
    public void deleteOngoingSessions() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(DatabaseContract.ChargeSessionEntry.TABLE_NAME,
                 DatabaseContract.ChargeSessionEntry.COLUMN_IS_ONGOING + " = ?",
                 new String[]{"1"});
    }

    /**
     * 更新会话的进行中状态
     */
    public void updateSessionOngoingStatus(long sessionId, boolean isOngoing) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_IS_ONGOING, isOngoing ? 1 : 0);
        db.update(DatabaseContract.ChargeSessionEntry.TABLE_NAME, values,
                 DatabaseContract.ChargeSessionEntry.COLUMN_ID + " = ?",
                 new String[]{String.valueOf(sessionId)});
    }

    /**
     * 部分更新会话（用于进行中会话的定期更新）
     * 不改变 is_ongoing 状态，不触发统计更新
     */
    public void updateSessionPartial(ChargeSession session) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_END_TIMESTAMP, session.getEndTimestamp());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_END_LEVEL, session.getEndLevel());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_END_CHARGE_COUNTER, session.getEndChargeCounter());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_MAX_TEMPERATURE, session.getMaxTemperature());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_MIN_TEMPERATURE, session.getMinTemperature());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_DURATION, session.getScreenOnDuration());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_LEVEL_CHANGE, session.getScreenOnLevelChange());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_CHARGE_COUNTER_DIFF, session.getScreenOnChargeCounterDiff());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_DURATION, session.getDozeDuration());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_CHARGE_COUNTER_DIFF, session.getDozeChargeCounterDiff());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_ESTIMATED_CAPACITY, session.getEstimatedCapacity());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_CYCLE_COUNT, session.getCycleCount());

        db.update(DatabaseContract.ChargeSessionEntry.TABLE_NAME, values,
                 DatabaseContract.ChargeSessionEntry.COLUMN_ID + " = ?",
                 new String[]{String.valueOf(session.getId())});
    }

    /**
     * 当会话ID尚未写回缓存时，更新最新一条进行中会话（按开始时间降序取一条）
     */
    public void updateLatestOngoingPartial(ChargeSession session) {
        SQLiteDatabase db = getWritableDatabase();
        // 查询最新一条ongoing的id
        String q = "SELECT " + DatabaseContract.ChargeSessionEntry.COLUMN_ID +
                " FROM " + DatabaseContract.ChargeSessionEntry.TABLE_NAME +
                " WHERE " + DatabaseContract.ChargeSessionEntry.COLUMN_IS_ONGOING + " = 1" +
                " ORDER BY " + DatabaseContract.ChargeSessionEntry.COLUMN_START_TIMESTAMP + " DESC LIMIT 1";
        Cursor c = db.rawQuery(q, null);
        if (!c.moveToFirst()) {
            c.close();
            return;
        }
        long id = c.getLong(0);
        c.close();

        ContentValues values = new ContentValues();
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_END_TIMESTAMP, session.getEndTimestamp());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_END_LEVEL, session.getEndLevel());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_END_CHARGE_COUNTER, session.getEndChargeCounter());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_MAX_TEMPERATURE, session.getMaxTemperature());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_MIN_TEMPERATURE, session.getMinTemperature());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_DURATION, session.getScreenOnDuration());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_LEVEL_CHANGE, session.getScreenOnLevelChange());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_SCREEN_ON_CHARGE_COUNTER_DIFF, session.getScreenOnChargeCounterDiff());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_DURATION, session.getDozeDuration());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_DOZE_CHARGE_COUNTER_DIFF, session.getDozeChargeCounterDiff());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_ESTIMATED_CAPACITY, session.getEstimatedCapacity());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_CYCLE_COUNT, session.getCycleCount());

        db.update(DatabaseContract.ChargeSessionEntry.TABLE_NAME, values,
                DatabaseContract.ChargeSessionEntry.COLUMN_ID + " = ?",
                new String[]{String.valueOf(id)});
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

        // 更新聚合数据（仅充电阶段）
        if (session.getSessionType() == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE) {
            updateDailyStats(db, session);
            updateWeeklyStats(db, session);
            updateMonthlyStats(db, session);
        }
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
    
    // ==================== 聚合数据操作 ====================
    
    /**
     * 更新每日统计
     */
    private void updateDailyStats(SQLiteDatabase db, ChargeSession session) {
        String date = formatDate(session.getEndTimestamp(), "yyyy-MM-dd");
        updateStatsTable(db, DatabaseContract.DailyStatsEntry.TABLE_NAME,
                        DatabaseContract.DailyStatsEntry.COLUMN_DATE,
                        date, session);
    }
    
    /**
     * 更新每周统计
     */
    private void updateWeeklyStats(SQLiteDatabase db, ChargeSession session) {
        String weekStart = getWeekStart(session.getEndTimestamp());
        updateStatsTable(db, DatabaseContract.WeeklyStatsEntry.TABLE_NAME,
                        DatabaseContract.WeeklyStatsEntry.COLUMN_WEEK_START,
                        weekStart, session);
    }
    
    /**
     * 更新每月统计
     */
    private void updateMonthlyStats(SQLiteDatabase db, ChargeSession session) {
        String yearMonth = formatDate(session.getEndTimestamp(), "yyyy-MM");
        updateStatsTable(db, DatabaseContract.MonthlyStatsEntry.TABLE_NAME,
                        DatabaseContract.MonthlyStatsEntry.COLUMN_YEAR_MONTH,
                        yearMonth, session);
    }
    
    /**
     * 通用更新统计表
     */
    private void updateStatsTable(SQLiteDatabase db, String tableName, 
                                  String dateColumn, String dateValue, ChargeSession session) {
        // 检查是否已存在记录
        Cursor cursor = db.query(tableName, null,
                                dateColumn + " = ?", new String[]{dateValue},
                                null, null, null);
        
        if (cursor.moveToFirst()) {
            // 更新现有记录
            int sessionCount = cursor.getInt(cursor.getColumnIndexOrThrow(
                tableName.equals(DatabaseContract.DailyStatsEntry.TABLE_NAME) ? 
                DatabaseContract.DailyStatsEntry.COLUMN_SESSION_COUNT :
                tableName.equals(DatabaseContract.WeeklyStatsEntry.TABLE_NAME) ?
                DatabaseContract.WeeklyStatsEntry.COLUMN_SESSION_COUNT :
                DatabaseContract.MonthlyStatsEntry.COLUMN_SESSION_COUNT));
            
            int totalLevelChange = cursor.getInt(cursor.getColumnIndexOrThrow(
                tableName.equals(DatabaseContract.DailyStatsEntry.TABLE_NAME) ? 
                DatabaseContract.DailyStatsEntry.COLUMN_TOTAL_LEVEL_CHANGE :
                tableName.equals(DatabaseContract.WeeklyStatsEntry.TABLE_NAME) ?
                DatabaseContract.WeeklyStatsEntry.COLUMN_TOTAL_LEVEL_CHANGE :
                DatabaseContract.MonthlyStatsEntry.COLUMN_TOTAL_LEVEL_CHANGE));
            
            int totalChargeCounterDiff = cursor.getInt(cursor.getColumnIndexOrThrow(
                tableName.equals(DatabaseContract.DailyStatsEntry.TABLE_NAME) ? 
                DatabaseContract.DailyStatsEntry.COLUMN_TOTAL_CHARGE_COUNTER_DIFF :
                tableName.equals(DatabaseContract.WeeklyStatsEntry.TABLE_NAME) ?
                DatabaseContract.WeeklyStatsEntry.COLUMN_TOTAL_CHARGE_COUNTER_DIFF :
                DatabaseContract.MonthlyStatsEntry.COLUMN_TOTAL_CHARGE_COUNTER_DIFF));
            
            ContentValues values = new ContentValues();
            values.put(DatabaseContract.DailyStatsEntry.COLUMN_SESSION_COUNT, sessionCount + 1);
            values.put(DatabaseContract.DailyStatsEntry.COLUMN_TOTAL_LEVEL_CHANGE, 
                      totalLevelChange + session.getLevelChange());
            values.put(DatabaseContract.DailyStatsEntry.COLUMN_TOTAL_CHARGE_COUNTER_DIFF, 
                      totalChargeCounterDiff + session.getChargeCounterDiff());
            values.put(DatabaseContract.DailyStatsEntry.COLUMN_ESTIMATED_CAPACITY, session.getEstimatedCapacity());
            values.put(DatabaseContract.DailyStatsEntry.COLUMN_CYCLE_COUNT, session.getCycleCount());
            
            db.update(tableName, values, dateColumn + " = ?", new String[]{dateValue});
        } else {
            // 插入新记录
            ContentValues values = new ContentValues();
            values.put(dateColumn, dateValue);
            values.put(DatabaseContract.DailyStatsEntry.COLUMN_SESSION_COUNT, 1);
            values.put(DatabaseContract.DailyStatsEntry.COLUMN_TOTAL_LEVEL_CHANGE, session.getLevelChange());
            values.put(DatabaseContract.DailyStatsEntry.COLUMN_TOTAL_CHARGE_COUNTER_DIFF, session.getChargeCounterDiff());
            values.put(DatabaseContract.DailyStatsEntry.COLUMN_ESTIMATED_CAPACITY, session.getEstimatedCapacity());
            values.put(DatabaseContract.DailyStatsEntry.COLUMN_CYCLE_COUNT, session.getCycleCount());
            
            db.insert(tableName, null, values);
        }
        cursor.close();
    }
    
    /**
     * 格式化日期
     */
    private String formatDate(long timestamp, String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.getDefault());
        return sdf.format(timestamp);
    }
    
    /**
     * 获取周一日期
     */
    private String getWeekStart(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
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
    public List<WeeklyStats> getWeeklyStats(int offset, int limit) {
        SQLiteDatabase db = getReadableDatabase();
        List<WeeklyStats> stats = new ArrayList<>();
        
        String query = "SELECT * FROM " + DatabaseContract.WeeklyStatsEntry.TABLE_NAME +
                      " ORDER BY " + DatabaseContract.WeeklyStatsEntry.COLUMN_WEEK_START + " DESC" +
                      " LIMIT ? OFFSET ?";
        
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(limit), String.valueOf(offset)});
        
        while (cursor.moveToNext()) {
            WeeklyStats stat = new WeeklyStats();
            stat.setWeekStart(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.WeeklyStatsEntry.COLUMN_WEEK_START)));
            stat.setSessionCount(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.WeeklyStatsEntry.COLUMN_SESSION_COUNT)));
            stat.setTotalLevelChange(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.WeeklyStatsEntry.COLUMN_TOTAL_LEVEL_CHANGE)));
            stat.setTotalChargeCounterDiff(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.WeeklyStatsEntry.COLUMN_TOTAL_CHARGE_COUNTER_DIFF)));
            stat.setEstimatedCapacity(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.WeeklyStatsEntry.COLUMN_ESTIMATED_CAPACITY)));
            stat.setCycleCount(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.WeeklyStatsEntry.COLUMN_CYCLE_COUNT)));
            stats.add(stat);
        }
        cursor.close();
        
        return stats;
    }
    
    /**
     * 获取每月统计数据
     */
    public List<MonthlyStats> getMonthlyStats(int offset, int limit) {
        SQLiteDatabase db = getReadableDatabase();
        List<MonthlyStats> stats = new ArrayList<>();
        
        String query = "SELECT * FROM " + DatabaseContract.MonthlyStatsEntry.TABLE_NAME +
                      " ORDER BY " + DatabaseContract.MonthlyStatsEntry.COLUMN_YEAR_MONTH + " DESC" +
                      " LIMIT ? OFFSET ?";
        
        Cursor cursor = db.rawQuery(query, new String[]{String.valueOf(limit), String.valueOf(offset)});
        
        while (cursor.moveToNext()) {
            MonthlyStats stat = new MonthlyStats();
            stat.setYearMonth(cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.MonthlyStatsEntry.COLUMN_YEAR_MONTH)));
            stat.setSessionCount(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.MonthlyStatsEntry.COLUMN_SESSION_COUNT)));
            stat.setTotalLevelChange(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.MonthlyStatsEntry.COLUMN_TOTAL_LEVEL_CHANGE)));
            stat.setTotalChargeCounterDiff(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.MonthlyStatsEntry.COLUMN_TOTAL_CHARGE_COUNTER_DIFF)));
            stat.setEstimatedCapacity(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.MonthlyStatsEntry.COLUMN_ESTIMATED_CAPACITY)));
            stat.setCycleCount(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.MonthlyStatsEntry.COLUMN_CYCLE_COUNT)));
            stats.add(stat);
        }
        cursor.close();
        
        return stats;
    }
    
    // ==================== 聚合数据类 ====================
    
    /**
     * 每日统计数据类
     */
    public static class DailyStats {
        private String date;
        private int sessionCount;
        private int totalLevelChange;
        private int totalChargeCounterDiff;
        private int estimatedCapacity;
        private int cycleCount;
        
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
    }
    
    /**
     * 每周统计数据类
     */
    public static class WeeklyStats {
        private String weekStart;
        private int sessionCount;
        private int totalLevelChange;
        private int totalChargeCounterDiff;
        private int estimatedCapacity;
        private int cycleCount;
        
        public String getWeekStart() { return weekStart; }
        public void setWeekStart(String weekStart) { this.weekStart = weekStart; }
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
    }
    
    /**
     * 每月统计数据类
     */
    public static class MonthlyStats {
        private String yearMonth;
        private int sessionCount;
        private int totalLevelChange;
        private int totalChargeCounterDiff;
        private int estimatedCapacity;
        private int cycleCount;
        
        public String getYearMonth() { return yearMonth; }
        public void setYearMonth(String yearMonth) { this.yearMonth = yearMonth; }
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
    }
}

