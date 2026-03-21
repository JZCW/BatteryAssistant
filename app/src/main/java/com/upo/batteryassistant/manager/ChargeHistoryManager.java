package com.upo.batteryassistant.manager;

import android.content.Context;
import android.util.Log;

import com.upo.batteryassistant.data.BatteryInfo;
import com.upo.batteryassistant.data.ChargeSession;
import com.upo.batteryassistant.data.DailyStats;
import com.upo.batteryassistant.data.StateInfo;
import com.upo.batteryassistant.database.BatteryDatabaseHelper;
import com.upo.batteryassistant.database.DatabaseContract;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private DailyStats dailyStatsCache;
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
     * 获取指定结束时间之前的最新一条会话
     */
    public ChargeSession getLastSessionEndBefore(long endTimestampInclusive) {
        return dbHelper.getLastSessionEndBefore(endTimestampInclusive);
    }

    /**
     * 统计开始时间大于指定值的会话数量
     */
    public int countSessionsStartAfter(long startTimestamp) {
        return dbHelper.countSessionsStartAfter(startTimestamp);
    }

    /**
     * 获取每日统计数据
     */
    public List<DailyStats> getDailyStats(int offset, int limit) {
        return dbHelper.getDailyStats(offset, limit);
    }

    /**
     * 获取每周统计数据
     */
    public List<DailyStats> getWeeklyStats(int offset, int limit) {
        return dbHelper.getWeeklyStats(offset, limit);
    }

    /**
     * 获取每月统计数据
     */
    public List<DailyStats> getMonthlyStats(int offset, int limit) {
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
            recoverDailyStatsCache(currentInfo);
            firstInit = false;
        }

        // 如果没有会话，开始新会话
        if (currentSessionCache == null) {
            startNewSession(currentInfo, currentState.isCharging());
        }
        if (dailyStatsCache == null) {
            startNewDailyStats(currentInfo);
        }

        long now = currentInfo.getTimestamp();
        long duration = now - currentSessionCache.getEndTimestamp();

        // 更新基础信息
        currentSessionCache.setPauseTimestamp(now);
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

        // 累加分状态数据
        if ((lastBatteryInfoCache == null) && (currentSessionCache.getCounter()>0)) {
            Log.e("ChargeHistoryManager", "lastBatteryInfoCache is null but counter is " + currentSessionCache.getCounter());
            currentSessionCache.markInvalid();
        }
        if ((!currentSessionCache.isSessionInvalid()) && (currentSessionCache.getCounter()>0)) {
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
                if (lastScreenOn) {
                    currentSessionCache.setScreenOnDuration(
                        currentSessionCache.getScreenOnDuration() + duration);
                    currentSessionCache.setScreenOnChargeCounterDiff(
                        currentSessionCache.getScreenOnChargeCounterDiff() + chargeCounterDiff);
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

        // 更新历史统计
        updateDailyStats(currentInfo);

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
        dbHelper.updateDailyStats(dailyStatsCache);
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
        currentSessionCache.setStartTimestamp(info.getTimestamp());
        currentSessionCache.setPauseTimestamp(info.getTimestamp());
        currentSessionCache.setEndTimestamp(info.getTimestamp());
        currentSessionCache.setStartLevel(info.getLevel());
        currentSessionCache.setEndLevel(info.getLevel());
        currentSessionCache.setStartChargeCounter(info.getChargeCounter());
        currentSessionCache.setEndChargeCounter(info.getChargeCounter());
        currentSessionCache.setMaxTemperature(info.getTemperature());
        currentSessionCache.setMinTemperature(info.getTemperature());
        currentSessionCache.setOngoing(true);

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

        // 更新统计数据中的估算信息
        if (dailyStatsCache != null) {
            int level = session.getLevelChange();
            if ((level > 0) && (level > dailyStatsCache.getMaxLevelChange())) {
                dailyStatsCache.setMaxLevelChange(level);
                int count = session.getChargeCounterDiff();
                int capacity = count*100/level;
                dailyStatsCache.setEstimatedCapacity(capacity);
            }
        }

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
            ongoingSession.markInvalid();
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
     * 恢复日统计缓存
     */
    private void recoverDailyStatsCache(BatteryInfo currentInfo) {
        String dateStr = getDateStr(currentInfo.getTimestamp());
        dailyStatsCache = dbHelper.getDailyStats(dateStr);
    }

    /**
     * 开始新的日统计
     * @param currentInfo
     */
    private void startNewDailyStats(BatteryInfo currentInfo) {
        if (dailyStatsCache != null) {
            // 结束先前的统计
            endDailyStats();
        }
        dailyStatsCache = new DailyStats();
        dailyStatsCache.setDate(getDateStr(currentInfo.getTimestamp()));
    }

    /**
     * 结束当前日统计
     */
    private void endDailyStats() {
        if (dailyStatsCache != null) {
            if (!dbHelper.updateDailyStats(dailyStatsCache)) {
                Log.e(TAG, "更新日统计失败");
            }
            dailyStatsCache = null;
        }
    }

    /**
     * 更新日统计
     */
    private void updateDailyStats(BatteryInfo currentInfo) {
        if (dailyStatsCache == null) {
            startNewDailyStats(currentInfo);
        } else {
            String dateStr = getDateStr(currentInfo.getTimestamp());
            if (!dailyStatsCache.getDate().equals(dateStr)) {
                // 日期不同，需要重新开始
                startNewDailyStats(currentInfo);
            }
        }

        // 当且仅当充电状态下会话更新一次时计数
        if ((currentSessionCache.getSessionType() == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE) && (currentSessionCache.getCounter() == 1)) {
            dailyStatsCache.setSessionCount(dailyStatsCache.getSessionCount() + 1);
        }

        // 更新累加数据
        if (lastBatteryInfoCache != null) {
            dailyStatsCache.setTotalLevelChange(dailyStatsCache.getTotalLevelChange() + currentInfo.getLevel() - lastBatteryInfoCache.getLevel());
            dailyStatsCache.setTotalChargeCounterDiff(dailyStatsCache.getTotalChargeCounterDiff() + currentInfo.getChargeCounter() - lastBatteryInfoCache.getChargeCounter());
        }

        // 更新其他数据(取最大值)
        dailyStatsCache.setCapacity(Math.max(dailyStatsCache.getCapacity(), currentInfo.getFullCapacity()));
        dailyStatsCache.setCycleCount(Math.max(dailyStatsCache.getCycleCount(), currentInfo.getCycleCount()));
    }

    /**
     * 获取日期字符串
     */
    private String getDateStr(long timestamp) {
        return Instant.ofEpochMilli(timestamp)
                      .atZone(ZoneId.systemDefault()) // 使用系统时区
                      .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
}

