package com.upo.batteryassistant.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.upo.batteryassistant.data.BatteryInfo;
import com.upo.batteryassistant.data.ChargeSession;
import com.upo.batteryassistant.database.BatteryDatabaseHelper;
import com.upo.batteryassistant.database.DatabaseContract;

import java.util.List;

/**
 * 充放电历史管理器
 * 负责管理充放电阶段的记录和查询
 */
public class ChargeHistoryManager {
    private static final String TAG = "ChargeHistoryManager";
    private static ChargeHistoryManager instance;
    private Context context;
    private BatteryDatabaseHelper dbHelper;
    private SharedPreferences prefs;

    private static final String PREFS_NAME = "charge_history_prefs";

    // SharedPreferences键名
    private static final String KEY_CURRENT_SESSION_START_TIMESTAMP = "current_session_start_timestamp";
    private static final String KEY_CURRENT_SESSION_TYPE = "current_session_type";
    private static final String KEY_CURRENT_SESSION_START_LEVEL = "current_session_start_level";
    private static final String KEY_CURRENT_SESSION_START_CHARGE_COUNTER = "current_session_start_charge_counter";
    private static final String KEY_CURRENT_SESSION_MAX_TEMPERATURE = "current_session_max_temperature";
    private static final String KEY_CURRENT_SESSION_MIN_TEMPERATURE = "current_session_min_temperature";

    private ChargeHistoryManager(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = new BatteryDatabaseHelper(this.context);
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 获取单例实例
     */
    public static ChargeHistoryManager getInstance(Context context) {
        if (instance == null) {
            synchronized (ChargeHistoryManager.class) {
                if (instance == null) {
                    instance = new ChargeHistoryManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * 初始化（应用启动时调用）
     */
    public void init() {
        updateSession(DatabaseContract.ChargeSessionEntry.SESSION_TYPE_UNKNOWN);
    }

    /**
     * 充电器连接事件
     */
    public void onPowerConnected() {
        updateSession(DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE);
    }

    /**
     * 充电器断开事件
     */
    public void onPowerDisconnected() {
        updateSession(DatabaseContract.ChargeSessionEntry.SESSION_TYPE_DISCHARGE);
    }

    /**
     * 更新阶段记录
     */
    private void updateSession(int chargeType) {
        // 从SharedPreferences读取开始状态
        long startTimestamp = prefs.getLong(KEY_CURRENT_SESSION_START_TIMESTAMP, -1);
        int startSessionType = prefs.getInt(KEY_CURRENT_SESSION_TYPE, -1);
        int startLevel = prefs.getInt(KEY_CURRENT_SESSION_START_LEVEL, -1);
        int startChargeCounter = prefs.getInt(KEY_CURRENT_SESSION_START_CHARGE_COUNTER, -1);
        int maxTemperature = prefs.getInt(KEY_CURRENT_SESSION_MAX_TEMPERATURE, -1);
        int minTemperature = prefs.getInt(KEY_CURRENT_SESSION_MIN_TEMPERATURE, -1);

        // 获取当前电池信息
        BatteryInfo info = BatteryInfoManager.getInstance(context).getCurrentBatteryInfo();
        if (info == null) {
            Log.e(TAG, "无法获取电池信息");
            return;
        }
        
        long timestamp = System.currentTimeMillis();
        int newSessionType = (chargeType == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_UNKNOWN) ?
            (info.isCharging() ?
                DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE :
                DatabaseContract.ChargeSessionEntry.SESSION_TYPE_DISCHARGE) :
            chargeType;
        long duration = timestamp - startTimestamp;

        // 更新温度统计
        int currentTemp = info.getTemperature();
        if (maxTemperature < 0 || currentTemp > maxTemperature) {
            maxTemperature = currentTemp;
        }
        if (minTemperature < 0 || currentTemp < minTemperature) {
            minTemperature = currentTemp;
        }

        // 更新当前状态
        if (newSessionType == startSessionType && duration < 4000) { // 过滤相同状态的短记录
            // 仍然需要更新温度统计
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(KEY_CURRENT_SESSION_MAX_TEMPERATURE, maxTemperature);
            editor.putInt(KEY_CURRENT_SESSION_MIN_TEMPERATURE, minTemperature);
            editor.apply();
            return;
        }
        
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(KEY_CURRENT_SESSION_START_TIMESTAMP, timestamp);
        editor.putInt(KEY_CURRENT_SESSION_TYPE, newSessionType);
        editor.putInt(KEY_CURRENT_SESSION_START_LEVEL, info.getLevel());
        editor.putInt(KEY_CURRENT_SESSION_START_CHARGE_COUNTER, info.getChargeCounter());
        editor.putInt(KEY_CURRENT_SESSION_MAX_TEMPERATURE, currentTemp);
        editor.putInt(KEY_CURRENT_SESSION_MIN_TEMPERATURE, currentTemp);
        editor.apply();

        // 保存阶段记录
        if (startTimestamp < 0 || startSessionType < 0 || startLevel < 0) {
            Log.e(TAG, "数据不完整，无法保存");
            return;
        }

        int levelChange = info.getLevel() - startLevel;
        // 仅在电量变化无法判断时使用记录状态
        int sessionType = (levelChange == 0) ?
            startSessionType :
            (levelChange > 0 ?
                DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE :
                DatabaseContract.ChargeSessionEntry.SESSION_TYPE_DISCHARGE);

        ChargeSession session = new ChargeSession();
        session.setStartTimestamp(startTimestamp);
        session.setEndTimestamp(timestamp);
        session.setSessionType(sessionType);
        session.setStartLevel(startLevel);
        session.setEndLevel(info.getLevel());
        session.setStartChargeCounter(startChargeCounter);
        session.setEndChargeCounter(info.getChargeCounter());
        session.setMaxTemperature(maxTemperature);
        session.setMinTemperature(minTemperature);
        
        // 仅充电阶段设置估计容量和周期计数
        if (sessionType == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE) {
            session.setEstimatedCapacity(info.getFullCapacity());
            session.setCycleCount(info.getCycleCount());
        }

        // 在后台线程执行数据库操作
        new Thread(() -> {
            dbHelper.insertSession(session);
        }).start();
    }

    /**
     * 分页查询充放电阶段
     */
    public List<ChargeSession> getSessions(int offset, int limit) {
        return dbHelper.getSessions(offset, limit);
    }

    /**
     * 获取总记录数
     */
    public int getSessionCount() {
        return dbHelper.getSessionCount();
    }
    
    /**
     * 获取每日统计数据
     */
    public List<BatteryDatabaseHelper.DailyStats> getDailyStats(int offset, int limit) {
        return dbHelper.getDailyStats(offset, limit);
    }
    
    /**
     * 获取每周统计数据
     */
    public List<BatteryDatabaseHelper.WeeklyStats> getWeeklyStats(int offset, int limit) {
        return dbHelper.getWeeklyStats(offset, limit);
    }
    
    /**
     * 获取每月统计数据
     */
    public List<BatteryDatabaseHelper.MonthlyStats> getMonthlyStats(int offset, int limit) {
        return dbHelper.getMonthlyStats(offset, limit);
    }
}

