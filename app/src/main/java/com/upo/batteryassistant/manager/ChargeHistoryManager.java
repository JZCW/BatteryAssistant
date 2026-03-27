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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
    private static final long PERSIST_SYNC_TIMEOUT_MS = 1000;
    private static final long INSERT_WAIT_TIMEOUT_MS = 1000;

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
    private final Object cacheLock = new Object();
    private final ExecutorService dbIo = Executors.newSingleThreadExecutor(r -> new Thread(r, "BA-DB-IO"));
    private Future<Long> currentSessionInsertFuture;

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
        List<ChargeSession> sessions = runDbTask(() -> dbHelper.getSessions(offset, limit));
        return sessions == null ? Collections.emptyList() : sessions;
    }

    /**
     * 刷新前强一致查询：先持久化再查询
     */
    public List<ChargeSession> getSessionsFresh(int offset, int limit) {
        forcePersistNowSync();
        return getSessions(offset, limit);
    }

    /**
     * 获取总记录数
     */
    public int getSessionCount() {
        Integer count = runDbTask(() -> dbHelper.getSessionCount());
        return count == null ? 0 : count;
    }

    /**
     * 获取指定结束时间之前的最新一条会话
     */
    public ChargeSession getLastSessionEndBefore(long endTimestampInclusive) {
        return runDbTask(() -> dbHelper.getLastSessionEndBefore(endTimestampInclusive));
    }

    /**
     * 统计开始时间大于指定值的会话数量
     */
    public int countSessionsStartAfter(long startTimestamp) {
        Integer count = runDbTask(() -> dbHelper.countSessionsStartAfter(startTimestamp));
        return count == null ? 0 : count;
    }

    /**
     * 获取每日统计数据
     */
    public List<DailyStats> getDailyStats(int offset, int limit) {
        List<DailyStats> stats = runDbTask(() -> dbHelper.getDailyStats(offset, limit));
        return stats == null ? Collections.emptyList() : stats;
    }

    public List<DailyStats> getDailyStatsFresh(int offset, int limit) {
        forcePersistNowSync();
        return getDailyStats(offset, limit);
    }

    /**
     * 获取每周统计数据
     */
    public List<DailyStats> getWeeklyStats(int offset, int limit) {
        List<DailyStats> stats = runDbTask(() -> dbHelper.getWeeklyStats(offset, limit));
        return stats == null ? Collections.emptyList() : stats;
    }

    public List<DailyStats> getWeeklyStatsFresh(int offset, int limit) {
        forcePersistNowSync();
        return getWeeklyStats(offset, limit);
    }

    /**
     * 获取每月统计数据
     */
    public List<DailyStats> getMonthlyStats(int offset, int limit) {
        List<DailyStats> stats = runDbTask(() -> dbHelper.getMonthlyStats(offset, limit));
        return stats == null ? Collections.emptyList() : stats;
    }

    public List<DailyStats> getMonthlyStatsFresh(int offset, int limit) {
        forcePersistNowSync();
        return getMonthlyStats(offset, limit);
    }

    /**
     * 更新当前会话缓存（累加分状态数据）
     */
    public void updateCurrentSession(BatteryInfo currentInfo, StateInfo currentState) {
        if (currentInfo == null) {
            return;
        }

        synchronized (cacheLock) {
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
            if ((lastBatteryInfoCache == null) && (currentSessionCache.getCounter() > 0)) {
                Log.e("ChargeHistoryManager", "lastBatteryInfoCache is null but counter is " + currentSessionCache.getCounter());
                currentSessionCache.markInvalid();
            }
            if ((!currentSessionCache.isSessionInvalid()) && (currentSessionCache.getCounter() > 0)) {
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
                // 异步提交持久化任务，避免阻塞 updateCurrentSession
                submitDbTask(this::persistCurrentSessionInternal);
            }
        }
    }

    /**
     * 持久化当前会话到数据库（部分更新，不改变 is_ongoing）
     */
    private void persistCurrentSessionInternal() {
        ChargeSession session;
        BatteryInfo batteryInfo;
        synchronized (cacheLock) {
            session = currentSessionCache;
            batteryInfo = lastBatteryInfoCache;
        }
        if (session == null || batteryInfo == null) {
            return;
        }

        long now;
        long persistedTs;
        int persistedLevel;
        synchronized (cacheLock) {
            now = batteryInfo.getTimestamp();
            persistedTs = lastPersistTimestamp;
            persistedLevel = lastPersistLevel;
        }

        // 判断是否需要持久化：仅当时间间隔和电量变化均未达到阈值时跳过
        boolean timeEnough = (now - persistedTs) >= PERSIST_INTERVAL;
        boolean levelEnough = Math.abs(batteryInfo.getLevel() - persistedLevel) >= PERSIST_LEVEL_THRESHOLD;
        if (!timeEnough && !levelEnough) {
            return;
        }

        // 持久化
        forcePersistNowInternal();
    }

    /**
     * 立即持久化当前会话到数据库（部分更新）
     */
    public void forcePersistNow() {
        forcePersistNowSync();
    }

    public boolean forcePersistNowSync() {
        return forcePersistNowSync(PERSIST_SYNC_TIMEOUT_MS);
    }

    public boolean forcePersistNowSync(long timeoutMs) {
        Future<Boolean> task = forcePersistNowAsync();
        try {
            return task.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            Log.w(TAG, "forcePersistNowSync timeout", e);
        } catch (ExecutionException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Log.e(TAG, "forcePersistNowSync failed", e);
        }
        return false;
    }

    public Future<Boolean> forcePersistNowAsync() {
        return dbIo.submit(this::forcePersistNowInternal);
    }

    private boolean forcePersistNowInternal() {
        ChargeSession session;
        DailyStats dailyStats;
        BatteryInfo batteryInfo;
        Future<Long> insertFuture;
        synchronized (cacheLock) {
            session = currentSessionCache;
            dailyStats = dailyStatsCache;
            batteryInfo = lastBatteryInfoCache;
            insertFuture = currentSessionInsertFuture;
        }

        if (session == null || batteryInfo == null) {
            return false;
        }

        if (insertFuture != null) {
            try {
                Long id = insertFuture.get(INSERT_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (id == null || id <= 0) {
                    Log.w(TAG, "session insert id invalid, skip persist");
                    return false;
                }
            } catch (TimeoutException e) {
                Log.w(TAG, "wait insert id timeout, skip persist", e);
                return false;
            } catch (ExecutionException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                Log.e(TAG, "wait insert id failed", e);
                return false;
            }
        }

        if (session.getId() <= 0) {
            Log.w(TAG, "session id not ready, skip persist");
            return false;
        }

        dbHelper.updateSessionPartial(session);
        dbHelper.updateDailyStats(dailyStats);
        synchronized (cacheLock) {
            if (lastBatteryInfoCache != null) {
                lastPersistTimestamp = lastBatteryInfoCache.getTimestamp();
                lastPersistLevel = lastBatteryInfoCache.getLevel();
            }
        }
        Log.d(TAG, "Force persisted session: " + session.getId());
        return true;
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
        currentSessionInsertFuture = dbIo.submit(() -> {
            long id = dbHelper.insertOngoingSession(session);
            synchronized (cacheLock) {
                session.setId(id);
            }
            return id;
        });

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
        submitDbTask(() -> {
            forcePersistNowInternal();
            if (session.getId() > 0) {
                dbHelper.finishSession(session);
            }
        });

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
        currentSessionInsertFuture = null;
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
        List<ChargeSession> ongoingSessions = runDbTask(() -> dbHelper.getOngoingSessions());
        if (ongoingSessions == null) {
            return;
        }

        if (ongoingSessions.isEmpty()) {
            // 没有进行中的会话
            return;
        }

        if (ongoingSessions.size() > 1) {
            // 多条 ongoing，异常情况，全部结束并标记分状态无效
            Log.w(TAG, "发现多条进行中会话，全部结束");
            for (ChargeSession session : ongoingSessions) {
                session.setOngoing(false);
                submitDbTask(() -> dbHelper.finishSession(session));
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
            submitDbTask(() -> dbHelper.finishSession(ongoingSession));

            Log.i(TAG, "不恢复会话");
        }
    }

    /**
     * 恢复日统计缓存
     */
    private void recoverDailyStatsCache(BatteryInfo currentInfo) {
        String dateStr = getDateStr(currentInfo.getTimestamp());
        dailyStatsCache = runDbTask(() -> dbHelper.getDailyStats(dateStr));
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
            DailyStats stats = dailyStatsCache;
            Boolean updated = runDbTask(() -> dbHelper.updateDailyStats(stats));
            if (updated == null || !updated) {
                Log.e(TAG, "更新日统计失败");
            }
            dailyStatsCache = null;
        }
    }

    private void submitDbTask(Runnable runnable) {
        dbIo.submit(() -> {
            try {
                runnable.run();
            } catch (Exception e) {
                Log.e(TAG, "db task failed", e);
            }
        });
    }

    private <T> T runDbTask(Callable<T> callable) {
        Future<T> future = dbIo.submit(callable);
        try {
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            Log.e(TAG, "db task failed", e);
            return null;
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

        // 仅当充电时更新统计信息
        if (currentSessionCache.getSessionType() != DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE) {
            return;
        }

        // 且仅当会话更新一次时计数
        if (currentSessionCache.getCounter() == 1) {
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

