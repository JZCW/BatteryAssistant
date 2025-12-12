package com.upo.batteryassistant.manager;

import android.content.Context;
import android.content.SharedPreferences;
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
    private static ChargeHistoryManager instance;
    private Context context;
    private BatteryDatabaseHelper dbHelper;
    private SharedPreferences prefs;
    
    private static final String PREFS_NAME = "charge_history_prefs";
    
    // SharedPreferences键名
    private static final String KEY_CURRENT_SESSION_START_TIMESTAMP = "current_session_start_timestamp";
    private static final String KEY_CURRENT_SESSION_TYPE = "current_session_type";
    private static final String KEY_CURRENT_SESSION_START_LEVEL = "current_session_start_level";
    private static final String KEY_CURRENT_SESSION_START_TEMPERATURE = "current_session_start_temperature";
    private static final String KEY_CURRENT_SESSION_START_VOLTAGE = "current_session_start_voltage";
    private static final String KEY_CURRENT_SESSION_START_CURRENT = "current_session_start_current";
    
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
        // 读取当前进行中阶段的开始状态
        long currentStartTimestamp = prefs.getLong(KEY_CURRENT_SESSION_START_TIMESTAMP, -1);
        if (currentStartTimestamp < 0) {
            // 没有进行中的阶段，记录当前状态作为新阶段的开始
            BatteryInfo info = BatteryInfoManager.getInstance(context).getCurrentBatteryInfo();
            if (info != null) {
                int sessionType = info.isCharging() ? 
                    DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE : 
                    DatabaseContract.ChargeSessionEntry.SESSION_TYPE_DISCHARGE;
                startNewSession(sessionType, info);
            }
            return;
        }
        
        // 检查是否有未完成的阶段需要恢复
        checkAndRecoverUnfinishedSession();
    }
    
    /**
     * 检查并恢复未完成的阶段
     */
    private void checkAndRecoverUnfinishedSession() {
        BatteryInfo currentInfo = BatteryInfoManager.getInstance(context).getCurrentBatteryInfo();
        if (currentInfo == null) return;
        
        // 读取当前进行中阶段的开始状态
        long currentStartTimestamp = prefs.getLong(KEY_CURRENT_SESSION_START_TIMESTAMP, -1);
        int currentSessionType = prefs.getInt(KEY_CURRENT_SESSION_TYPE, -1);
        
        if (currentStartTimestamp < 0 || currentSessionType < 0) {
            // 没有进行中的阶段，记录当前状态
            int sessionType = currentInfo.isCharging() ? 
                DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE : 
                DatabaseContract.ChargeSessionEntry.SESSION_TYPE_DISCHARGE;
            startNewSession(sessionType, currentInfo);
            return;
        }
        
        // 判断当前状态与保存的开始状态是否一致
        boolean currentlyCharging = currentInfo.isCharging();
        boolean savedWasCharging = (currentSessionType == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE);
        
        if (currentlyCharging != savedWasCharging) {
            // 状态不一致，说明发生了事件但应用没有捕获到
            // 将上一阶段存入数据库
            saveCompletedSession(currentStartTimestamp, currentInfo);
            
            // 开始新阶段
            int newSessionType = currentlyCharging ? 
                DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE : 
                DatabaseContract.ChargeSessionEntry.SESSION_TYPE_DISCHARGE;
            startNewSession(newSessionType, currentInfo);
        }
        // 如果状态一致，说明阶段仍在进行中，不需要做任何操作
    }
    
    /**
     * 充电器连接事件
     */
    public void onPowerConnected() {
        BatteryInfo currentInfo = BatteryInfoManager.getInstance(context).getCurrentBatteryInfo();
        if (currentInfo == null) return;
        
        // 从SharedPreferences读取上一阶段的开始状态
        long lastStartTimestamp = prefs.getLong(KEY_CURRENT_SESSION_START_TIMESTAMP, -1);
        if (lastStartTimestamp >= 0) {
            // 有上一阶段，将其存入数据库
            int lastSessionType = prefs.getInt(KEY_CURRENT_SESSION_TYPE, -1);
            if (lastSessionType == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_DISCHARGE) {
                // 上一阶段是放电，现在开始充电，结束放电阶段
                saveCompletedSession(lastStartTimestamp, currentInfo);
            }
        }
        
        // 开始新的充电阶段（只保存到SharedPreferences）
        startNewSession(DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE, currentInfo);
    }
    
    /**
     * 充电器断开事件
     */
    public void onPowerDisconnected() {
        BatteryInfo currentInfo = BatteryInfoManager.getInstance(context).getCurrentBatteryInfo();
        if (currentInfo == null) return;
        
        // 从SharedPreferences读取上一阶段的开始状态
        long lastStartTimestamp = prefs.getLong(KEY_CURRENT_SESSION_START_TIMESTAMP, -1);
        if (lastStartTimestamp >= 0) {
            // 有上一阶段，将其存入数据库
            int lastSessionType = prefs.getInt(KEY_CURRENT_SESSION_TYPE, -1);
            if (lastSessionType == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE) {
                // 上一阶段是充电，现在开始放电，结束充电阶段
                saveCompletedSession(lastStartTimestamp, currentInfo);
            }
        }
        
        // 开始新的放电阶段（只保存到SharedPreferences）
        startNewSession(DatabaseContract.ChargeSessionEntry.SESSION_TYPE_DISCHARGE, currentInfo);
    }
    
    /**
     * 开始新阶段（只保存到SharedPreferences，不存入数据库）
     */
    private void startNewSession(int sessionType, BatteryInfo info) {
        SharedPreferences.Editor editor = prefs.edit();
        long timestamp = System.currentTimeMillis();
        
        editor.putLong(KEY_CURRENT_SESSION_START_TIMESTAMP, timestamp);
        editor.putInt(KEY_CURRENT_SESSION_TYPE, sessionType);
        editor.putInt(KEY_CURRENT_SESSION_START_LEVEL, info.getLevel());
        editor.putInt(KEY_CURRENT_SESSION_START_TEMPERATURE, info.getTemperature());
        editor.putInt(KEY_CURRENT_SESSION_START_VOLTAGE, info.getVoltage());
        editor.putInt(KEY_CURRENT_SESSION_START_CURRENT, info.getCurrent());
        editor.apply();
    }
    
    /**
     * 保存完整阶段到数据库
     */
    private void saveCompletedSession(long startTimestamp, BatteryInfo endInfo) {
        // 从SharedPreferences读取开始状态
        int sessionType = prefs.getInt(KEY_CURRENT_SESSION_TYPE, -1);
        int startLevel = prefs.getInt(KEY_CURRENT_SESSION_START_LEVEL, -1);
        int startTemperature = prefs.getInt(KEY_CURRENT_SESSION_START_TEMPERATURE, -1);
        int startVoltage = prefs.getInt(KEY_CURRENT_SESSION_START_VOLTAGE, -1);
        int startCurrent = prefs.getInt(KEY_CURRENT_SESSION_START_CURRENT, -1);
        
        if (sessionType < 0 || startLevel < 0) {
            // 数据不完整，无法保存
            return;
        }
        
        long endTimestamp = System.currentTimeMillis();
        long duration = endTimestamp - startTimestamp;
        int levelChange = endInfo.getLevel() - startLevel;
        
        ChargeSession session = new ChargeSession();
        session.setStartTimestamp(startTimestamp);
        session.setEndTimestamp(endTimestamp);
        session.setSessionType(sessionType);
        session.setDuration(duration);
        session.setStartLevel(startLevel);
        session.setStartTemperature(startTemperature);
        session.setStartVoltage(startVoltage);
        session.setStartCurrent(startCurrent);
        session.setEndLevel(endInfo.getLevel());
        session.setEndTemperature(endInfo.getTemperature());
        session.setEndVoltage(endInfo.getVoltage());
        session.setEndCurrent(endInfo.getCurrent());
        session.setLevelChange(levelChange);
        
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
}

