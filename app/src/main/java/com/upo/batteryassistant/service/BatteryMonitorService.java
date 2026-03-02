package com.upo.batteryassistant.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.upo.batteryassistant.R;
import com.upo.batteryassistant.data.BatteryInfo;
import com.upo.batteryassistant.data.ChargeSession;
import com.upo.batteryassistant.database.BatteryDatabaseHelper;
import com.upo.batteryassistant.database.DatabaseContract;
import com.upo.batteryassistant.manager.BatteryInfoManager;
import com.upo.batteryassistant.manager.ChargeHistoryManager;
import com.upo.batteryassistant.ui.MainActivity;

import java.util.List;

/**
 * 电池监控服务（前台服务）
 * 负责在后台持续监控电池状态并显示通知
 */
public class BatteryMonitorService extends Service {
    private static final String TAG = "BatteryMonitorService";
    private static final String CHANNEL_ID = "BatteryMonitorChannel";
    private static final int NOTIFICATION_ID = 1;

    // 持久化间隔配置
    private static final long PERSIST_INTERVAL = 60 * 1000; // 60秒
    private static final int PERSIST_LEVEL_THRESHOLD = 1; // 电量变化1%触发持久化

    private BatteryInfoManager batteryInfoManager;
    private ChargeHistoryManager chargeHistoryManager;
    private BatteryDatabaseHelper dbHelper;
    private BroadcastReceiver powerReceiver;
    private BroadcastReceiver screenStateReceiver;
    private BroadcastReceiver dozeReceiver;
    private NotificationManager notificationManager;
    private PowerManager powerManager;
    private Handler updateHandler;
    private Runnable updateRunnable;

    // 当前状态
    private boolean isScreenOn = true;
    private boolean isCharging = false;

    // 会话缓存
    private ChargeSession currentSessionCache;
    private BatteryInfo lastBatteryInfoCache;
    private long lastPersistTimestamp;
    private int lastPersistLevel;

    // 更新间隔配置（单位：毫秒）
    // 非充电时
    private static final long DATA_FETCH_INTERVAL_NOT_CHARGING_SCREEN_ON = 10000; // 10秒
    private static final long DATA_FETCH_INTERVAL_NOT_CHARGING_SCREEN_OFF = 300000; // 5分钟
    // 充电时
    private static final long DATA_FETCH_INTERVAL_CHARGING_SCREEN_ON = 5000; // 5秒
    private static final long DATA_FETCH_INTERVAL_CHARGING_SCREEN_OFF = 30000; // 30秒

    @Override
    public void onCreate() {
        super.onCreate();

        batteryInfoManager = BatteryInfoManager.getInstance(this);
        chargeHistoryManager = ChargeHistoryManager.getInstance(this);
        dbHelper = new BatteryDatabaseHelper(this);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        updateHandler = new Handler(Looper.getMainLooper());

        // 创建通知渠道（Android 8.0+）
        createNotificationChannel();

        // 初始化充放电历史管理器
        chargeHistoryManager.init();

        // 恢复异常中断的会话
        recoverOngoingSessions();

        // 注册充电器事件监听
        registerPowerReceiver();

        // 注册屏幕状态监听
        registerScreenStateReceiver();

        // 注册 Doze 状态监听
        registerDozeReceiver();

        // 启动定时更新任务
        startPeriodicUpdate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 启动前台服务
        BatteryInfo currentInfo = batteryInfoManager.getCurrentBatteryInfo();
        startForeground(NOTIFICATION_ID, createNotification(currentInfo));
        return START_STICKY; // 服务被杀死后自动重启
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterPowerReceiver();
        unregisterScreenStateReceiver();
        unregisterDozeReceiver();
        stopNotificationUpdate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "电池监控",
                NotificationManager.IMPORTANCE_LOW // 低优先级，不发出声音
            );
            channel.setDescription("显示电池状态信息");
            channel.setShowBadge(false);
            channel.enableLights(false);
            channel.enableVibration(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

        
    /**
     * 注册充电器事件广播接收器
     */
    private void registerPowerReceiver() {
        if (powerReceiver != null) {
            return;
        }

        powerReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                    isCharging = true;
                    onChargingStateChanged(true);
                } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                    isCharging = false;
                    onChargingStateChanged(false);
                }
                // 充电状态变化时重新调度更新任务
                restartPeriodicUpdate();
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(powerReceiver, filter);
    }
    
    /**
     * 注销充电器事件广播接收器
     */
    private void unregisterPowerReceiver() {
        if (powerReceiver != null) {
            try {
                unregisterReceiver(powerReceiver);
                powerReceiver = null;
            } catch (IllegalArgumentException e) {
                // 接收器未注册，忽略
            }
        }
    }

    /**
     * 注册屏幕状态监听
     */
    private void registerScreenStateReceiver() {
        if (screenStateReceiver != null) {
            return;
        }

        screenStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    isScreenOn = true;
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    isScreenOn = false;
                }
                // 屏幕状态变化时重新调度更新任务
                restartPeriodicUpdate();
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenStateReceiver, filter);
    }

    /**
     * 注销屏幕状态监听
     */
    private void unregisterScreenStateReceiver() {
        if (screenStateReceiver != null) {
            try {
                unregisterReceiver(screenStateReceiver);
                screenStateReceiver = null;
            } catch (IllegalArgumentException e) {
                // 接收器未注册，忽略
            }
        }
    }

    /**
     * 注册 Doze 状态监听
     */
    private void registerDozeReceiver() {
        if (dozeReceiver != null) {
            return;
        }

        dozeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED.equals(action)) {
                    onDozeStateChanged();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
        registerReceiver(dozeReceiver, filter);
    }

    /**
     * 注销 Doze 状态监听
     */
    private void unregisterDozeReceiver() {
        if (dozeReceiver != null) {
            try {
                unregisterReceiver(dozeReceiver);
                dozeReceiver = null;
            } catch (IllegalArgumentException e) {
                // 接收器未注册，忽略
            }
        }
    }

    /**
     * 停止通知更新
     */
    private void stopNotificationUpdate() {
        if (updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
            updateRunnable = null;
        }
    }

    /**
     * 启动定时更新任务
     */
    private void startPeriodicUpdate() {
        if (updateRunnable != null) {
            return;
        }
        
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                long nextUpdateInterval = updateBatteryInfo();;
                updateHandler.postDelayed(this, nextUpdateInterval);
            }
        };
        
        // 立即执行一次
        updateHandler.post(updateRunnable);
    }

    /**
     * 重启定时更新任务
     */
    private void restartPeriodicUpdate() {
        stopNotificationUpdate();
        startPeriodicUpdate();
    }

    /**
     * 更新电池信息并刷新通知
     * @return 下次更新间隔
     */
    private long updateBatteryInfo() {
        boolean shouldUpdateNotification = true;
        long nextUpdateInterval;

        if (isCharging) {
            if (isScreenOn) {
                nextUpdateInterval = DATA_FETCH_INTERVAL_CHARGING_SCREEN_ON;
            } else {
                nextUpdateInterval = DATA_FETCH_INTERVAL_CHARGING_SCREEN_OFF;
            }
        } else {
            if (isScreenOn) {
                nextUpdateInterval = DATA_FETCH_INTERVAL_NOT_CHARGING_SCREEN_ON;
            } else {
                shouldUpdateNotification = false; // 非充电 + 关屏 获取数据但不更新通知
                nextUpdateInterval = DATA_FETCH_INTERVAL_NOT_CHARGING_SCREEN_OFF;
            }
        }

        BatteryInfo currentInfo = batteryInfoManager.getCurrentBatteryInfo();
        if (currentInfo == null) {
            return nextUpdateInterval;
        }

        // 更新当前会话缓存
        updateCurrentSession(currentInfo);

        // 检查是否需要持久化
        if (shouldPersist(currentInfo)) {
            persistCurrentSession();
        }

        // 根据策略决定是否更新通知
        if (shouldUpdateNotification) {
            notificationManager.notify(NOTIFICATION_ID, createNotification(currentInfo));
        }

        return nextUpdateInterval;
    }

    /**
     * 更新当前会话缓存（累加分状态数据）
     */
    private void updateCurrentSession(BatteryInfo currentInfo) {
        if (currentSessionCache == null) {
            return;
        }

        long now = System.currentTimeMillis();
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
        if (lastBatteryInfoCache != null) {
            int levelDiff = currentInfo.getLevel() - lastBatteryInfoCache.getLevel();
            int chargeCounterDiff = 0;
            if (currentInfo.getChargeCounter() >= 0 && lastBatteryInfoCache.getChargeCounter() >= 0) {
                chargeCounterDiff = currentInfo.getChargeCounter() - lastBatteryInfoCache.getChargeCounter();
            }

            // 屏幕开启期间累加
            if (isScreenOn) {
                currentSessionCache.setScreenOnDuration(
                    currentSessionCache.getScreenOnDuration() + duration);
                if (currentSessionCache.getSessionType() == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_DISCHARGE) {
                    currentSessionCache.setScreenOnLevelChange(
                        currentSessionCache.getScreenOnLevelChange() - levelDiff);
                } else if (currentSessionCache.getSessionType() == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE) {
                    currentSessionCache.setScreenOnChargeCounterDiff(
                        currentSessionCache.getScreenOnChargeCounterDiff() + chargeCounterDiff);
                }
            }
        }

        // 更新缓存
        lastBatteryInfoCache = currentInfo;

        // 更新充电会话的容量和周期数
        if (currentSessionCache.getSessionType() == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE) {
            currentSessionCache.setEstimatedCapacity(currentInfo.getFullCapacity());
            currentSessionCache.setCycleCount(currentInfo.getCycleCount());
        }
    }

    /**
     * 判断是否需要持久化
     */
    private boolean shouldPersist(BatteryInfo currentInfo) {
        if (currentSessionCache == null) {
            return false;
        }

        long now = System.currentTimeMillis();

        // 时间间隔阈值
        if (now - lastPersistTimestamp >= PERSIST_INTERVAL) {
            return true;
        }

        // 电量变化阈值
        if (Math.abs(currentInfo.getLevel() - lastPersistLevel) >= PERSIST_LEVEL_THRESHOLD) {
            return true;
        }

        return false;
    }

    /**
     * 持久化当前会话到数据库（部分更新，不改变 is_ongoing）
     */
    private void persistCurrentSession() {
        if (currentSessionCache == null) {
            return;
        }

        final ChargeSession session = currentSessionCache;
        new Thread(() -> {
            dbHelper.updateSessionPartial(session);
        }).start();

        lastPersistTimestamp = System.currentTimeMillis();
        if (lastBatteryInfoCache != null) {
            lastPersistLevel = lastBatteryInfoCache.getLevel();
        }
    }

    /**
     * 创建通知
     */
    private Notification createNotification(BatteryInfo batteryInfo) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_battery_notification) // 使用电池图标
            .setContentIntent(pendingIntent)
            .setOngoing(true) // 常驻通知
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .setAutoCancel(false); // 不自动取消

        if (batteryInfo != null) {
            // 通知标题
            String title = String.format("电池: %d%%", batteryInfo.getLevel());

            // 通知内容
            StringBuilder content = new StringBuilder();
            content.append(batteryInfo.getStatusText());

            if (batteryInfo.getVoltage() > 0) {
                content.append(" | ").append(String.format("%.2fV", batteryInfo.getVoltageVolts()));
            }

            if (batteryInfo.getTemperature() > 0) {
                content.append(" | ").append(String.format("%.1f°C", batteryInfo.getTemperatureCelsius()));
            }

            // 如果正在充电，显示剩余充电时间
            if (batteryInfo.isCharging() && batteryInfo.getChargeTimeRemaining() >= 0) {
                content.append("\n").append("预计充满: ").append(batteryInfo.getChargeTimeRemainingText());
            }

            builder.setContentTitle(title)
                   .setContentText(content.toString())
                   .setStyle(new NotificationCompat.BigTextStyle().bigText(content.toString()));
        } else {
            builder.setContentTitle("电池监控")
                   .setContentText("正在获取电池信息...");
        }

        return builder.build();
    }

    // ==================== 会话管理 ====================

    /**
     * 充电状态变化处理
     */
    private void onChargingStateChanged(boolean isNowCharging) {
        BatteryInfo currentInfo = batteryInfoManager.getCurrentBatteryInfo();
        if (currentInfo == null) {
            return;
        }

        int newSessionType = isNowCharging ?
            DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE :
            DatabaseContract.ChargeSessionEntry.SESSION_TYPE_DISCHARGE;

        // 如果有当前会话且类型不同，结束它
        if (currentSessionCache != null) {
            int currentType = currentSessionCache.getSessionType();
            if ((currentType == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE && !isNowCharging) ||
                (currentType == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_DISCHARGE && isNowCharging)) {
                finishCurrentSession();
            }
        }

        // 开始新会话
        startNewSession(newSessionType, currentInfo);
    }

    /**
     * 开始新会话
     */
    private void startNewSession(int sessionType, BatteryInfo info) {
        // 如果已有进行中会话，先结束
        if (currentSessionCache != null) {
            finishCurrentSession();
        }

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
        currentSessionCache.setScreenOnLevelChange(0);
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

        // 初始化缓存和持久化状态
        lastBatteryInfoCache = info;
        lastPersistTimestamp = System.currentTimeMillis();
        lastPersistLevel = info.getLevel();

        Log.i(TAG, "开始新会话: " + (sessionType == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE ? "充电" : "放电"));
    }

    /**
     * 结束当前会话
     */
    private void finishCurrentSession() {
        if (currentSessionCache == null) {
            return;
        }

        // 最后一次更新
        BatteryInfo currentInfo = batteryInfoManager.getCurrentBatteryInfo();
        if (currentInfo != null) {
            updateCurrentSession(currentInfo);
        }

        // 标记为已完成
        currentSessionCache.setOngoing(false);

        // 根据电量变化确定最终类型
        int levelChange = currentSessionCache.getEndLevel() - currentSessionCache.getStartLevel();
        if (levelChange > 0) {
            currentSessionCache.setSessionType(DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE);
        } else if (levelChange < 0) {
            currentSessionCache.setSessionType(DatabaseContract.ChargeSessionEntry.SESSION_TYPE_DISCHARGE);
        }

        // 写入数据库并更新统计
        final ChargeSession session = currentSessionCache;
        new Thread(() -> {
            dbHelper.finishSession(session);
        }).start();

        Log.i(TAG, "结束会话: " + session.getSessionTypeText() + 
              ", 电量变化: " + levelChange + "%");

        // 清空缓存
        currentSessionCache = null;
        lastBatteryInfoCache = null;
    }

    /**
     * Doze 状态变化处理
     * 注意：我们只能在退出 Doze 时收到事件，此时需要把从上次缓存到现在的这段时间标记为 Doze
     */
    private void onDozeStateChanged() {
        boolean isIdle = powerManager.isDeviceIdleMode();

        if (!isIdle && currentSessionCache != null && lastBatteryInfoCache != null) {
            // 退出 Doze：把从上次缓存到现在的这段时间标记为 Doze
            BatteryInfo currentInfo = batteryInfoManager.getCurrentBatteryInfo();
            if (currentInfo != null) {
                long now = System.currentTimeMillis();
                long dozeDuration = now - currentSessionCache.getEndTimestamp();

                int chargeCounterDiff = 0;
                if (currentInfo.getChargeCounter() >= 0 && lastBatteryInfoCache.getChargeCounter() >= 0) {
                    chargeCounterDiff = currentInfo.getChargeCounter() - lastBatteryInfoCache.getChargeCounter();
                }

                // 累加 Doze 数据（仅放电会话）
                if (currentSessionCache.getSessionType() == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_DISCHARGE) {
                    currentSessionCache.setDozeDuration(
                        currentSessionCache.getDozeDuration() + dozeDuration);
                    currentSessionCache.setDozeChargeCounterDiff(
                        currentSessionCache.getDozeChargeCounterDiff() + chargeCounterDiff);
                }

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

                // 更新缓存
                lastBatteryInfoCache = currentInfo;

                Log.d(TAG, "Doze 结束，时长: " + dozeDuration + "ms");
            }
        }
    }

    /**
     * 恢复异常中断的会话
     */
    private void recoverOngoingSessions() {
        List<ChargeSession> ongoingSessions = dbHelper.getOngoingSessions();

        if (ongoingSessions.isEmpty()) {
            // 没有进行中的会话，根据当前状态创建新会话
            BatteryInfo currentInfo = batteryInfoManager.getCurrentBatteryInfo();
            if (currentInfo != null) {
                int sessionType = currentInfo.isCharging() ?
                    DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE :
                    DatabaseContract.ChargeSessionEntry.SESSION_TYPE_DISCHARGE;
                startNewSession(sessionType, currentInfo);
            }
            return;
        }

        if (ongoingSessions.size() > 1) {
            // 多条 ongoing，异常情况，全部结束并标记分状态无效
            Log.w(TAG, "发现多条进行中会话，全部结束");
            for (ChargeSession session : ongoingSessions) {
                markSessionInvalid(session);
                dbHelper.finishSession(session);
            }
            // 创建新会话
            BatteryInfo currentInfo = batteryInfoManager.getCurrentBatteryInfo();
            if (currentInfo != null) {
                int sessionType = currentInfo.isCharging() ?
                    DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE :
                    DatabaseContract.ChargeSessionEntry.SESSION_TYPE_DISCHARGE;
                startNewSession(sessionType, currentInfo);
            }
            return;
        }

        // 只有一条 ongoing
        ChargeSession ongoingSession = ongoingSessions.get(0);
        BatteryInfo currentInfo = batteryInfoManager.getCurrentBatteryInfo();

        if (currentInfo == null) {
            // 无法获取电池信息，结束会话并标记无效
            markSessionInvalid(ongoingSession);
            dbHelper.finishSession(ongoingSession);
            return;
        }

        // 检查是否可以恢复
        if (shouldRestoreSession(ongoingSession, currentInfo)) {
            // 恢复会话，但标记分状态无效
            ongoingSession.setScreenOnDuration(-1);
            ongoingSession.setScreenOnLevelChange(-1);
            ongoingSession.setScreenOnChargeCounterDiff(-1);
            ongoingSession.setDozeDuration(-1);
            ongoingSession.setDozeChargeCounterDiff(-1);

            currentSessionCache = ongoingSession;
            lastBatteryInfoCache = currentInfo;
            lastPersistTimestamp = System.currentTimeMillis();
            lastPersistLevel = currentInfo.getLevel();

            Log.i(TAG, "恢复进行中的会话，分状态标记为无效");
        } else {
            // 不能恢复，结束并创建新会话
            markSessionInvalid(ongoingSession);
            dbHelper.finishSession(ongoingSession);

            int sessionType = currentInfo.isCharging() ?
                DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE :
                DatabaseContract.ChargeSessionEntry.SESSION_TYPE_DISCHARGE;
            startNewSession(sessionType, currentInfo);

            Log.i(TAG, "不恢复会话，创建新会话");
        }
    }

    /**
     * 判断是否应该恢复会话
     */
    private boolean shouldRestoreSession(ChargeSession ongoingSession, BatteryInfo currentInfo) {
        // 检查时间是否过长（超过24小时）
        long duration = System.currentTimeMillis() - ongoingSession.getStartTimestamp();
        if (duration > 24 * 60 * 60 * 1000) {
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
            return false;
        }

        // 检查充电状态是否一致
        boolean currentCharging = currentInfo.isCharging();
        boolean sessionCharging = (ongoingSession.getSessionType() == DatabaseContract.ChargeSessionEntry.SESSION_TYPE_CHARGE);
        if (currentCharging != sessionCharging) {
            return false;
        }

        return true;
    }

    /**
     * 标记会话的分状态数据为无效
     */
    private void markSessionInvalid(ChargeSession session) {
        session.setScreenOnDuration(-1);
        session.setScreenOnLevelChange(-1);
        session.setScreenOnChargeCounterDiff(-1);
        session.setDozeDuration(-1);
        session.setDozeChargeCounterDiff(-1);
        session.setOngoing(false);
        session.setEndTimestamp(System.currentTimeMillis());
    }
}

