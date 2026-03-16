package com.upo.batteryassistant.manager;

import android.content.Context;
import android.util.Log;

import com.upo.batteryassistant.data.BatteryInfo;
import com.upo.batteryassistant.data.ChargeSession;
import com.upo.batteryassistant.data.StateInfo;
import com.upo.batteryassistant.database.BatteryDatabaseHelper;
import com.upo.batteryassistant.database.DatabaseContract;

import java.util.List;

/**
 * 充放电历史管理器
 * 负责查询充放电阶段的记录和统计数据
 */
public class ChargeHistoryManager {
    private static final String TAG = "ChargeHistoryManager";

    private static ChargeHistoryManager instance;
    private BatteryDatabaseHelper dbHelper;

    // 持久化间隔配置
    private static final long PERSIST_INTERVAL = 60 * 1000; // 60秒
    private static final int PERSIST_LEVEL_THRESHOLD = 1; // 电量变化1%触发持久化

    // 会话缓存
    private ChargeSession currentSessionCache;
    private BatteryInfo lastBatteryInfoCache;
    private boolean lastIsIdle;
    private boolean lastIsCharging;
    private boolean lastScreenOn;
    private long lastPersistTimestamp;
    private int lastPersistLevel;
    private boolean firstInit;

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
     */
    public void init() {
        firstInit = true;
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

    /**
     * 更新当前会话缓存（累加分状态数据）
     */
    public void updateCurrentSession(BatteryInfo currentInfo, StateInfo currentState) {
        if (currentInfo == null) {
            return;
        }

        // 如果是首次初始化，尝试恢复异常中断的会话
        if (firstInit) {
            recoverOngoingSessions(currentInfo, currentState.isCharging());
            firstInit = false;
        }

        // 如果没有会话，开始新会话
        if (currentSessionCache == null) {
            startNewSession(currentInfo, currentState.isCharging());
        }

        long now = currentInfo.getTimestamp();
        long duration = now - currentSessionCache.getEndTimestamp();

        // 更新基础信息
        currentSessionCache.setEndTimestamp(now);
        currentSessionCache.setEndLevel(currentInfo.getLevel());
        currentSessionCache.setEndChargeCounter(currentInfo.getChargeCounter());

        // 更新温度
        int temp = currentInfo.getTemperature();
        if (currentSessionCache.getMaxTemperature() < 0 || temp > currentSessionCache.getMaxTemperature()) {
            currentSessionCache.setMaxTemperature(temp);
        }
        if (currentSessionCache.getMinTemperature() < 0 || temp < currentSessionCache.getMinTemperature()) {
            currentSessionCache.setMinTemperature(temp);
        }

        // 累加分状态数据（仅在有上次缓存时）
        if ((lastBatteryInfoCache != null) && (!isSessionInvalid(currentSessionCache))) {
            int chargeCounterDiff = 0;
            if (currentInfo.getChargeCounter() >= 0 && lastBatteryInfoCache.getChargeCounter() >= 0) {
                chargeCounterDiff = currentInfo.getChargeCounter() - lastBatteryInfoCache.getChargeCounter();
            }

            if (lastIsIdle) {
                // 计入Doze区间
                currentSessionCache.setDozeDuration(
                    currentSessionCache.getDozeDuration() + duration);
                currentSessionCache.setDozeChargeCounterDiff(
                    currentSessionCache.getDozeChargeCounterDiff() + chargeCounterDiff);
            } else {
                if (lastIsCharging) {
                    // 充电区间
                    if (lastScreenOn) {
                        currentSessionCache.setScreenOnDuration(
                            currentSessionCache.getScreenOnDuration() + duration);
                        currentSessionCache.setScreenOnChargeCounterDiff(
                            currentSessionCache.getScreenOnChargeCounterDiff() + chargeCounterDiff);
                    }
                }
                else {
                    // 放电区间
                    if (lastScreenOn) {
                        currentSessionCache.setScreenOnDuration(
                            currentSessionCache.getScreenOnDuration() + duration);
                        currentSessionCache.setScreenOnChargeCounterDiff(
                            currentSessionCache.getScreenOnChargeCounterDiff() + chargeCounterDiff); //FIXME 共用计数器
                    }
                }
            }
        }


        // 更新充电会话的容量和周期数
        if (currentSessionCache.getSessionType() == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE) {
            currentSessionCache.setEstimatedCapacity(currentInfo.getFullCapacity());
            currentSessionCache.setCycleCount(currentInfo.getCycleCount());
        }

        // 增加更新计数
        currentSessionCache.incrementCounter();

        // 更新缓存
        lastBatteryInfoCache = currentInfo;
        lastScreenOn = currentState.isScreenOn();
        lastIsCharging = currentState.isCharging();
        lastIsIdle = currentState.isIdle();

        // 如果状态变化，开始新会话
        int currentType = currentSessionCache.getSessionType();
        if ((currentType == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE && !lastIsCharging) ||
            (currentType == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_DISCHARGE && lastIsCharging)) {
            startNewSession(currentInfo, lastIsCharging);
        } else {
            // 否则检查是否需要持久化
            new Thread(() -> {
                persistCurrentSession();
            }).start();
        }
    }

    /**
     * 持久化当前会话到数据库（部分更新，不改变 is_ongoing）
     */
    private void persistCurrentSession() {
        long now = lastBatteryInfoCache.getTimestamp();

        // 判断是否需要持久化：仅当时间间隔和电量变化均未达到阈值时跳过
        boolean timeEnough = (now - lastPersistTimestamp) >= PERSIST_INTERVAL;
        boolean levelEnough = Math.abs(lastBatteryInfoCache.getLevel() - lastPersistLevel) >= PERSIST_LEVEL_THRESHOLD;
        if (!timeEnough && !levelEnough) {
            return;
        }

        // 持久化
        forcePersistNow();
    }

    /**
     * 立即持久化当前会话到数据库（部分更新）
     */
    public void forcePersistNow() {
        if (currentSessionCache == null) {
            return;
        }
        if (lastBatteryInfoCache == null) {
            return;
        }
        final ChargeSession session = currentSessionCache;
        dbHelper.updateSessionPartial(session);
        lastPersistTimestamp = lastBatteryInfoCache.getTimestamp();
        lastPersistLevel = lastBatteryInfoCache.getLevel();
        Log.d(TAG, "Force persisted session: " + session.getId());
    }

    /**
     * 开始新会话
     */
    private void startNewSession(BatteryInfo info, boolean isCharging) {
        // 如果已有进行中会话，先结束
        if (currentSessionCache != null) {
            finishCurrentSession();
        }

        int sessionType = isCharging ?
            DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE :
            DatabaseContract.ChargeSessionEntry.SESSION_TYPE_DISCHARGE;

        // 创建新会话缓存
        currentSessionCache = new ChargeSession();
        currentSessionCache.setSessionType(sessionType);
        currentSessionCache.setStartTimestamp(System.currentTimeMillis());
        currentSessionCache.setEndTimestamp(System.currentTimeMillis());
        currentSessionCache.setStartLevel(info.getLevel());
        currentSessionCache.setEndLevel(info.getLevel());
        currentSessionCache.setStartChargeCounter(info.getChargeCounter());
        currentSessionCache.setEndChargeCounter(info.getChargeCounter());
        currentSessionCache.setMaxTemperature(info.getTemperature());
        currentSessionCache.setMinTemperature(info.getTemperature());
        currentSessionCache.setOngoing(true);

        // 初始化分状态字段为 0
        currentSessionCache.setScreenOnDuration(0);
        currentSessionCache.setScreenOnChargeCounterDiff(0);
        currentSessionCache.setDozeDuration(0);
        currentSessionCache.setDozeChargeCounterDiff(0);

        // 充电会话设置容量和周期
        if (sessionType == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE) {
            currentSessionCache.setEstimatedCapacity(info.getFullCapacity());
            currentSessionCache.setCycleCount(info.getCycleCount());
        }

        // 插入数据库
        final ChargeSession session = currentSessionCache;
        new Thread(() -> {
            long id = dbHelper.insertOngoingSession(session);
            session.setId(id);
        }).start();

        Log.i(TAG, "开始新会话: " + (sessionType == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE ? "充电" : "放电"));
    }

    /**
     * 结束当前会话
     */
    private void finishCurrentSession() {
        if (currentSessionCache == null) {
            return;
        }

        // 标记为已完成
        currentSessionCache.setOngoing(false);

        // 写入数据库并更新统计
        final ChargeSession session = currentSessionCache;
        new Thread(() -> {
            dbHelper.finishSession(session);
        }).start();

        Log.i(TAG, "结束会话: " + session.getSessionTypeText());

        // 清空缓存
        currentSessionCache = null;
        lastBatteryInfoCache = null;
    }

    /**
     * 判断是否应该恢复会话
     */
    private boolean shouldRestoreSession(ChargeSession ongoingSession, BatteryInfo currentInfo, boolean isCharging) {
        // 检查间隔时间是否过长（超过5分钟）
        long duration = currentInfo.getTimestamp() - ongoingSession.getStartTimestamp();
        if (duration > 5 * 60 * 1000) {
            Log.d(TAG, "间隔时间过长，不恢复会话: " + duration + "ms");
            return false;
        }

        // 检查先前持续时间是否过长（超过20分钟）
        long previousDuration = ongoingSession.getEndTimestamp() - ongoingSession.getStartTimestamp();
        if (previousDuration > 20 * 60 * 1000) {
            Log.d(TAG, "先前持续时间过长，不恢复会话: " + previousDuration + "ms");
            return false;
        }

        // 检查电量变化方向是否一致
        int levelChange = currentInfo.getLevel() - ongoingSession.getStartLevel();
        boolean isMonotonic;
        if (ongoingSession.getSessionType() == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE) {
            isMonotonic = levelChange >= 0;
        } else {
            isMonotonic = levelChange <= 0;
        }
        if (!isMonotonic) {
            Log.d(TAG, "电量变化方向不一致，不恢复会话");
            return false;
        }

        // 检查充电状态是否一致
        boolean sessionCharging = (ongoingSession.getSessionType() == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE);
        if (isCharging != sessionCharging) {
            Log.d(TAG, "充电状态不一致，不恢复会话");
            return false;
        }

        return true;
    }

    /**
     * 恢复异常中断的会话
     */
    private void recoverOngoingSessions(BatteryInfo currentInfo, boolean isCharging) {
        List<ChargeSession> ongoingSessions = dbHelper.getOngoingSessions();

        if (ongoingSessions.isEmpty()) {
            // 没有进行中的会话
            return;
        }

        if (ongoingSessions.size() > 1) {
            // 多条 ongoing，异常情况，全部结束并标记分状态无效
            Log.w(TAG, "发现多条进行中会话，全部结束");
            for (ChargeSession session : ongoingSessions) {
                session.setOngoing(false);
                dbHelper.finishSession(session);
            }
            return;
        }

        // 只有一条 ongoing
        ChargeSession ongoingSession = ongoingSessions.get(0);

        // 检查是否可以恢复
        if (shouldRestoreSession(ongoingSession, currentInfo, isCharging)) {
            // 恢复会话，但标记分状态无效
            markSessionInvalid(ongoingSession);
            currentSessionCache = ongoingSession;

            Log.i(TAG, "恢复进行中的会话，分状态标记为无效");
        } else {
            // 不能恢复，结束并创建新会话
            ongoingSession.setOngoing(false);
            dbHelper.finishSession(ongoingSession);

            Log.i(TAG, "不恢复会话");
        }
    }

    /**
     * 标记会话的分状态数据为无效
     */
    private void markSessionInvalid(ChargeSession session) {
        session.setScreenOnDuration(-1);
        session.setScreenOnChargeCounterDiff(-1);
        session.setDozeDuration(-1);
        session.setDozeChargeCounterDiff(-1);
    }

    /**
     * 判断分状态数据是否无效
     */
    private boolean isSessionInvalid(ChargeSession session) {
        return session.getScreenOnDuration() == -1 ||
               session.getScreenOnChargeCounterDiff() == -1 ||
               session.getDozeDuration() == -1 ||
               session.getDozeChargeCounterDiff() == -1;
    }
}

