package com.upo.batteryassistant.manager;

import android.content.Context;

import com.upo.batteryassistant.data.ChargeSession;
import com.upo.batteryassistant.database.BatteryDatabaseHelper;

import java.util.List;

/**
 * 充放电历史管理器
 * 负责查询充放电阶段的记录和统计数据
 * 注：会话管理已移至 BatteryMonitorService
 */
public class ChargeHistoryManager {
    private static ChargeHistoryManager instance;
    private BatteryDatabaseHelper dbHelper;

    private ChargeHistoryManager(Context context) {
        this.dbHelper = new BatteryDatabaseHelper(context.getApplicationContext());
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
     * 注：会话管理已移至 BatteryMonitorService，此方法保留为空以保持兼容
     */
    public void init() {
        // 会话管理已移至 BatteryMonitorService
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

