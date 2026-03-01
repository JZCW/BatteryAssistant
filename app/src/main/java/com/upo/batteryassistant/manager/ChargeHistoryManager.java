package com.upo.batteryassistant.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
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
    private boolean isDeviceRebooted = false;  // 设备是否重启
    private Handler periodicSaveHandler;      // 定期保存Handler

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
        this.periodicSaveHandler = new Handler(Looper.getMainLooper());
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
        restoreOngoingSession();
        updateSession(DatabaseContract.ChargeSessionEntry.SESSION_TYPE_UNKNOWN);
        startPeriodicSave();
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

        // 如果有旧的进行中会话，先完成它
        if (startTimestamp >= 0 && startSessionType >= 0 && startLevel >= 0) {
            ChargeSession oldSession = new ChargeSession();
            oldSession.setStartTimestamp(startTimestamp);
            oldSession.setEndTimestamp(timestamp);
            oldSession.setSessionType(startSessionType);
            oldSession.setStartLevel(startLevel);
            oldSession.setEndLevel(info.getLevel());
            oldSession.setStartChargeCounter(startChargeCounter);
            oldSession.setEndChargeCounter(info.getChargeCounter());
            oldSession.setMaxTemperature(maxTemperature);
            oldSession.setMinTemperature(minTemperature);
            oldSession.setOngoing(false);

            int levelChange = info.getLevel() - startLevel;
            int sessionType = (levelChange == 0) ?
                startSessionType :
                (levelChange > 0 ?
                    DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE :
                    DatabaseContract.ChargeSessionEntry.SESSION_TYPE_DISCHARGE);
            oldSession.setSessionType(sessionType);

            if (sessionType == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE) {
                oldSession.setEstimatedCapacity(info.getFullCapacity());
                oldSession.setCycleCount(info.getCycleCount());
            }

            // 在后台线程执行数据库操作
            final ChargeSession sessionToSave = oldSession;
            new Thread(() -> {
                dbHelper.insertSession(sessionToSave);
            }).start();
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(KEY_CURRENT_SESSION_START_TIMESTAMP, timestamp);
        editor.putInt(KEY_CURRENT_SESSION_TYPE, newSessionType);
        editor.putInt(KEY_CURRENT_SESSION_START_LEVEL, info.getLevel());
        editor.putInt(KEY_CURRENT_SESSION_START_CHARGE_COUNTER, info.getChargeCounter());
        editor.putInt(KEY_CURRENT_SESSION_MAX_TEMPERATURE, currentTemp);
        editor.putInt(KEY_CURRENT_SESSION_MIN_TEMPERATURE, currentTemp);
        editor.apply();
    }

    /**
     * 保存/更新进行中的会话到数据库
     */
    public void saveOngoingSession() {
        long startTimestamp = prefs.getLong(KEY_CURRENT_SESSION_START_TIMESTAMP, -1);
        if (startTimestamp < 0) {
            return;  // 没有进行中的会话
        }

        BatteryInfo info = BatteryInfoManager.getInstance(context).getCurrentBatteryInfo();
        if (info == null) {
            return;
        }

        int sessionType = prefs.getInt(KEY_CURRENT_SESSION_TYPE, -1);
        int startLevel = prefs.getInt(KEY_CURRENT_SESSION_START_LEVEL, -1);
        int startChargeCounter = prefs.getInt(KEY_CURRENT_SESSION_START_CHARGE_COUNTER, -1);
        int maxTemperature = prefs.getInt(KEY_CURRENT_SESSION_MAX_TEMPERATURE, -1);
        int minTemperature = prefs.getInt(KEY_CURRENT_SESSION_MIN_TEMPERATURE, -1);

        ChargeSession session = new ChargeSession();
        session.setStartTimestamp(startTimestamp);
        session.setEndTimestamp(System.currentTimeMillis());
        session.setSessionType(sessionType);
        session.setStartLevel(startLevel);
        session.setEndLevel(info.getLevel());
        session.setStartChargeCounter(startChargeCounter);
        session.setEndChargeCounter(info.getChargeCounter());
        session.setMaxTemperature(maxTemperature);
        session.setMinTemperature(minTemperature);
        session.setOngoing(true);

        // 删除旧的进行中会话，插入新的
        new Thread(() -> {
            dbHelper.deleteOngoingSessions();
            dbHelper.insertSession(session);
        }).start();
    }

    /**
     * 恢复进行中的会话
     */
    private void restoreOngoingSession() {
        List<ChargeSession> ongoingSessions = dbHelper.getOngoingSessions();

        if (ongoingSessions.isEmpty()) {
            return;
        }

        ChargeSession ongoingSession = ongoingSessions.get(0);
        BatteryInfo currentInfo = BatteryInfoManager.getInstance(context).getCurrentBatteryInfo();

        if (currentInfo == null) {
            completeOngoingSession(ongoingSession);
            return;
        }

        if (shouldRestoreSession(ongoingSession, currentInfo)) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(KEY_CURRENT_SESSION_START_TIMESTAMP, ongoingSession.getStartTimestamp());
            editor.putInt(KEY_CURRENT_SESSION_TYPE, ongoingSession.getSessionType());
            editor.putInt(KEY_CURRENT_SESSION_START_LEVEL, ongoingSession.getStartLevel());
            editor.putInt(KEY_CURRENT_SESSION_START_CHARGE_COUNTER, ongoingSession.getStartChargeCounter());
            editor.putInt(KEY_CURRENT_SESSION_MAX_TEMPERATURE, ongoingSession.getMaxTemperature());
            editor.putInt(KEY_CURRENT_SESSION_MIN_TEMPERATURE, ongoingSession.getMinTemperature());
            editor.apply();

            Log.i(TAG, "恢复进行中的会话");
        } else {
            completeOngoingSession(ongoingSession);
            startNewSession(currentInfo);

            Log.i(TAG, "不恢复会话，创建新会话");
        }
    }

    /**
     * 完成进行中的会话
     */
    private void completeOngoingSession(ChargeSession session) {
        session.setOngoing(false);
        session.setEndTimestamp(System.currentTimeMillis());

        new Thread(() -> {
            dbHelper.updateSessionOngoingStatus(session.getId(), false);
        }).start();
    }

    /**
     * 设备重启时的处理
     */
    public void onDeviceReboot() {
        isDeviceRebooted = true;
        Log.i(TAG, "检测到设备重启");
    }

    /**
     * 判断是否应该恢复会话
     */
    private boolean shouldRestoreSession(ChargeSession ongoingSession, BatteryInfo currentInfo) {
        if (isDeviceRebooted) {
            return false;
        }

        long duration = System.currentTimeMillis() - ongoingSession.getStartTimestamp();
        if (duration > 24 * 60 * 60 * 1000) {
            return false;
        }

        int levelChange = currentInfo.getLevel() - ongoingSession.getStartLevel();
        boolean isMonotonic;
        if (ongoingSession.getSessionType() == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE) {
            isMonotonic = levelChange >= 0;
        } else {
            isMonotonic = levelChange <= 0;
        }
        if (!isMonotonic) {
            return false;
        }

        boolean currentCharging = currentInfo.isCharging();
        boolean sessionCharging = (ongoingSession.getSessionType() == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE);
        if (currentCharging != sessionCharging) {
            return false;
        }

        return true;
    }

    /**
     * 开始新会话
     */
    private void startNewSession(BatteryInfo info) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(KEY_CURRENT_SESSION_START_TIMESTAMP, System.currentTimeMillis());
        editor.putInt(KEY_CURRENT_SESSION_TYPE, info.isCharging() ?
            DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE :
            DatabaseContract.ChargeSessionEntry.SESSION_TYPE_DISCHARGE);
        editor.putInt(KEY_CURRENT_SESSION_START_LEVEL, info.getLevel());
        editor.putInt(KEY_CURRENT_SESSION_START_CHARGE_COUNTER, info.getChargeCounter());
        editor.putInt(KEY_CURRENT_SESSION_MAX_TEMPERATURE, info.getTemperature());
        editor.putInt(KEY_CURRENT_SESSION_MIN_TEMPERATURE, info.getTemperature());
        editor.apply();
    }

    /**
     * 启动定期保存
     */
    private void startPeriodicSave() {
        periodicSaveHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                saveOngoingSession();
                periodicSaveHandler.postDelayed(this, 5 * 60 * 1000);  // 5分钟
            }
        }, 5 * 60 * 1000);
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

