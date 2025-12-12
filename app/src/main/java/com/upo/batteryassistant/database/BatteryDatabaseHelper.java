package com.upo.batteryassistant.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.upo.batteryassistant.data.ChargeSession;

import java.util.ArrayList;
import java.util.List;

/**
 * 电池数据库Helper
 */
public class BatteryDatabaseHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "battery_assistant.db";
    private static final int DATABASE_VERSION = 1;
    
    // 创建charge_sessions表的SQL
    private static final String SQL_CREATE_CHARGE_SESSIONS_TABLE = 
        "CREATE TABLE " + DatabaseContract.ChargeSessionEntry.TABLE_NAME + " (" +
        DatabaseContract.ChargeSessionEntry.COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
        DatabaseContract.ChargeSessionEntry.COLUMN_START_TIMESTAMP + " INTEGER NOT NULL," +
        DatabaseContract.ChargeSessionEntry.COLUMN_END_TIMESTAMP + " INTEGER NOT NULL," +
        DatabaseContract.ChargeSessionEntry.COLUMN_SESSION_TYPE + " INTEGER NOT NULL," +
        DatabaseContract.ChargeSessionEntry.COLUMN_DURATION + " INTEGER NOT NULL," +
        DatabaseContract.ChargeSessionEntry.COLUMN_START_LEVEL + " INTEGER NOT NULL," +
        DatabaseContract.ChargeSessionEntry.COLUMN_START_TEMPERATURE + " INTEGER," +
        DatabaseContract.ChargeSessionEntry.COLUMN_START_VOLTAGE + " INTEGER," +
        DatabaseContract.ChargeSessionEntry.COLUMN_START_CURRENT + " INTEGER," +
        DatabaseContract.ChargeSessionEntry.COLUMN_END_LEVEL + " INTEGER NOT NULL," +
        DatabaseContract.ChargeSessionEntry.COLUMN_END_TEMPERATURE + " INTEGER," +
        DatabaseContract.ChargeSessionEntry.COLUMN_END_VOLTAGE + " INTEGER," +
        DatabaseContract.ChargeSessionEntry.COLUMN_END_CURRENT + " INTEGER," +
        DatabaseContract.ChargeSessionEntry.COLUMN_LEVEL_CHANGE + " INTEGER NOT NULL" +
        ");";
    
    // 创建索引
    private static final String SQL_CREATE_START_TIMESTAMP_INDEX = 
        "CREATE INDEX idx_start_timestamp ON " + 
        DatabaseContract.ChargeSessionEntry.TABLE_NAME + 
        "(" + DatabaseContract.ChargeSessionEntry.COLUMN_START_TIMESTAMP + " DESC);";
    
    private static final String SQL_CREATE_END_TIMESTAMP_INDEX = 
        "CREATE INDEX idx_end_timestamp ON " + 
        DatabaseContract.ChargeSessionEntry.TABLE_NAME + 
        "(" + DatabaseContract.ChargeSessionEntry.COLUMN_END_TIMESTAMP + " DESC);";
    
    private static final String SQL_CREATE_SESSION_TYPE_INDEX = 
        "CREATE INDEX idx_session_type ON " + 
        DatabaseContract.ChargeSessionEntry.TABLE_NAME + 
        "(" + DatabaseContract.ChargeSessionEntry.COLUMN_SESSION_TYPE + ");";
    
    public BatteryDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_CHARGE_SESSIONS_TABLE);
        db.execSQL(SQL_CREATE_START_TIMESTAMP_INDEX);
        db.execSQL(SQL_CREATE_END_TIMESTAMP_INDEX);
        db.execSQL(SQL_CREATE_SESSION_TYPE_INDEX);
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 未来版本升级时处理
    }
    
    /**
     * 插入充放电阶段
     */
    public long insertSession(ChargeSession session) {
        SQLiteDatabase db = getWritableDatabase();
        android.content.ContentValues values = new android.content.ContentValues();
        
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_START_TIMESTAMP, session.getStartTimestamp());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_END_TIMESTAMP, session.getEndTimestamp());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_SESSION_TYPE, session.getSessionType());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_DURATION, session.getDuration());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_START_LEVEL, session.getStartLevel());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_START_TEMPERATURE, session.getStartTemperature());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_START_VOLTAGE, session.getStartVoltage());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_START_CURRENT, session.getStartCurrent());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_END_LEVEL, session.getEndLevel());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_END_TEMPERATURE, session.getEndTemperature());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_END_VOLTAGE, session.getEndVoltage());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_END_CURRENT, session.getEndCurrent());
        values.put(DatabaseContract.ChargeSessionEntry.COLUMN_LEVEL_CHANGE, session.getLevelChange());
        
        return db.insert(DatabaseContract.ChargeSessionEntry.TABLE_NAME, null, values);
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
     * 从Cursor解析ChargeSession
     */
    private ChargeSession parseSessionFromCursor(Cursor cursor) {
        ChargeSession session = new ChargeSession();
        
        session.setId(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_ID)));
        session.setStartTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_START_TIMESTAMP)));
        session.setEndTimestamp(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_END_TIMESTAMP)));
        session.setSessionType(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_SESSION_TYPE)));
        session.setDuration(cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_DURATION)));
        session.setStartLevel(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_START_LEVEL)));
        session.setStartTemperature(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_START_TEMPERATURE)));
        session.setStartVoltage(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_START_VOLTAGE)));
        session.setStartCurrent(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_START_CURRENT)));
        session.setEndLevel(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_END_LEVEL)));
        session.setEndTemperature(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_END_TEMPERATURE)));
        session.setEndVoltage(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_END_VOLTAGE)));
        session.setEndCurrent(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_END_CURRENT)));
        session.setLevelChange(cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.ChargeSessionEntry.COLUMN_LEVEL_CHANGE)));
        
        return session;
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
}

