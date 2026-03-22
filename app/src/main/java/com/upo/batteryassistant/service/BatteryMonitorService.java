package com.upo.batteryassistant.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.upo.batteryassistant.R;
import com.upo.batteryassistant.data.BatteryInfo;
import com.upo.batteryassistant.data.StateInfo;

import com.upo.batteryassistant.manager.BatteryInfoManager;
import com.upo.batteryassistant.manager.ChargeHistoryManager;
import com.upo.batteryassistant.ui.MainActivity;

/**
 * 电池监控服务（前台服务）
 * 负责在后台持续监控电池状态并显示通知
 */
public class BatteryMonitorService extends Service {
    private static final String TAG = "BatteryMonitorService";
    private static final String CHANNEL_ID = "BatteryMonitorChannel";
    private static final int NOTIFICATION_ID = 1;
    private static final String PREFS_NAME = "battery_monitor_service_state";
    private static final String KEY_LAST_HEARTBEAT = "last_heartbeat";
    private static final String KEY_LAST_SCREEN_ON = "last_screen_on";
    private static final String KEY_LAST_IDLE = "last_idle";
    private static final String KEY_LAST_CHARGING = "last_charging";
    private static final String KEY_LAST_STATE_UPDATE = "last_state_update";
    private static final String KEY_LAST_START_SOURCE = "last_start_source";
    private static final String KEY_SERVICE_RUNNING = "service_running";
    private static final String KEY_LAST_ALARM_AT = "last_alarm_at";
    public static final String ACTION_RECOVERY_CHECK = "com.upo.batteryassistant.action.RECOVERY_CHECK";
    public static final String EXTRA_START_SOURCE = "start_source";
    public static final String START_SOURCE_APP = "app_launch";
    public static final String START_SOURCE_BOOT = "boot_receiver";
    public static final String START_SOURCE_ALARM = "recovery_alarm";
    public static final String START_SOURCE_TASK_REMOVED = "task_removed";
    private static final long RECOVERY_CHECK_INTERVAL_MS = 15 * 60 * 1000L;
    private static final long HEARTBEAT_STALE_THRESHOLD_MS = 20 * 60 * 1000L;

    private BatteryInfoManager batteryInfoManager;
    private ChargeHistoryManager chargeHistoryManager;

    private BroadcastReceiver powerReceiver;
    private BroadcastReceiver screenStateReceiver;
    private BroadcastReceiver dozeReceiver;
    private NotificationManager notificationManager;
    private PowerManager powerManager;
    private AlarmManager alarmManager;
    private SharedPreferences servicePrefs;
    private Handler updateHandler;
    private Runnable updateRunnable;
    private long lastScreenOffTimestamp = -1L;
    private long lastUpdateExecutionTimestamp = -1L;

    // 当前状态
    private StateInfo stateInfo;

    // 更新间隔配置（单位：毫秒）
    // 非充电时
    private static final long DATA_FETCH_INTERVAL_NOT_CHARGING_SCREEN_ON = 10000; // 10秒
    private static final long DATA_FETCH_INTERVAL_NOT_CHARGING_SCREEN_OFF_ACTIVE = 60000; // 1分钟
    private static final long DATA_FETCH_INTERVAL_NOT_CHARGING_SCREEN_OFF_STABLE = 120000; // 2分钟
    private static final long SCREEN_OFF_ACTIVE_WINDOW_MS = 15 * 60 * 1000L; // 熄屏后前15分钟更积极
    // 充电时
    private static final long DATA_FETCH_INTERVAL_CHARGING_SCREEN_ON = 5000; // 5秒
    private static final long DATA_FETCH_INTERVAL_CHARGING_SCREEN_OFF = 30000; // 30秒

    @Override
    public void onCreate() {
        super.onCreate();

        batteryInfoManager = BatteryInfoManager.getInstance(this);
        chargeHistoryManager = ChargeHistoryManager.getInstance(this);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        servicePrefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        updateHandler = new Handler(Looper.getMainLooper());

        markServiceRunning(true);
        Log.i(TAG, "Service onCreate, last heartbeat=" + servicePrefs.getLong(KEY_LAST_HEARTBEAT, -1));

        // 创建通知渠道（Android 8.0+）
        createNotificationChannel();

        // 初始化充放电历史管理器
        chargeHistoryManager.init();

        // 初始化状态信息
        initializeStateInfo();

        // 注册事件监听
        registerPowerReceiver();
        registerScreenStateReceiver();
        registerDozeReceiver();

        // 启动定时更新任务
        startPeriodicUpdate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String startSource = getStartSource(intent);
        persistStartSource(startSource);
        Log.i(TAG, "onStartCommand source=" + startSource + ", startId=" + startId + ", flags=" + flags);

        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification(null));
        persistCurrentState("onStartCommand");
        persistHeartbeat("onStartCommand");
        scheduleRecoveryCheck("onStartCommand");
        return START_STICKY; // 服务被杀死后自动重启
    }

    @Override
    public void onDestroy() {
        Log.w(TAG, "Service onDestroy");
        super.onDestroy();
        unregisterPowerReceiver();
        unregisterScreenStateReceiver();
        unregisterDozeReceiver();
        stopNotificationUpdate();
        markServiceRunning(false);
        persistCurrentState("onDestroy");
        scheduleRecoveryCheck("onDestroy");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.w(TAG, "Service onTaskRemoved");
        persistCurrentState("onTaskRemoved");
        scheduleRecoveryCheck("onTaskRemoved");
        super.onTaskRemoved(rootIntent);
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
                    stateInfo.setCharging(true);
                } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                    stateInfo.setCharging(false);
                }
                persistCurrentState("power:" + action);
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
                    stateInfo.setScreenOn(true);
                    lastScreenOffTimestamp = -1L;
                } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    stateInfo.setScreenOn(false);
                    lastScreenOffTimestamp = System.currentTimeMillis();
                }
                persistCurrentState("screen:" + action);
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
                    restartPeriodicUpdate();
                    stateInfo.setIdle(powerManager.isDeviceIdleMode());
                    persistCurrentState("doze:" + action);
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
                long now = System.currentTimeMillis();
                long delta = lastUpdateExecutionTimestamp > 0 ? (now - lastUpdateExecutionTimestamp) : -1L;
                lastUpdateExecutionTimestamp = now;
                long nextUpdateInterval = updateBatteryInfo();
                Log.d(TAG, "心跳执行完成: delta=" + delta + "ms, next=" + nextUpdateInterval
                    + "ms, state=" + stateInfo + ", screenOffAge=" + getScreenOffAgeMs());
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
     * 初始化状态信息
     */
    private void initializeStateInfo() {
        stateInfo = new StateInfo();
        
        // 获取当前充电状态
        IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, batteryFilter);
        if (batteryStatus != null) {
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                               status == BatteryManager.BATTERY_STATUS_FULL;
            stateInfo.setCharging(isCharging);
            Log.d(TAG, "初始充电状态: " + (isCharging ? "充电中" : "未充电"));
        }
        
        // 获取当前屏幕状态
        if (powerManager != null) {
            stateInfo.setScreenOn(powerManager.isInteractive());
            Log.d(TAG, "初始屏幕状态: " + (powerManager.isInteractive() ? "亮屏" : "灭屏"));
            if (!powerManager.isInteractive()) {
                lastScreenOffTimestamp = System.currentTimeMillis();
            }
        }

        // 获取当前Doze状态
        if (powerManager != null) {
            stateInfo.setIdle(powerManager.isDeviceIdleMode());
            Log.d(TAG, "初始Doze状态: " + (powerManager.isDeviceIdleMode() ? "Doze模式" : "正常模式"));
        }

        logRecoveredState();
        persistCurrentState("initializeStateInfo");

        Log.d(TAG, "状态信息初始化完成: " + stateInfo);
    }

    /**
     * 更新电池信息并刷新通知
     * @return 下次更新间隔
     */
    private long updateBatteryInfo() {
        boolean shouldUpdateNotification = true;
        long nextUpdateInterval;
        String intervalReason;

        if (stateInfo.isCharging()) {
            if (stateInfo.isScreenOn()) {
                nextUpdateInterval = DATA_FETCH_INTERVAL_CHARGING_SCREEN_ON;
                intervalReason = "charging_screen_on";
            } else {
                nextUpdateInterval = DATA_FETCH_INTERVAL_CHARGING_SCREEN_OFF;
                intervalReason = "charging_screen_off";
            }
        } else {
            if (stateInfo.isScreenOn()) {
                nextUpdateInterval = DATA_FETCH_INTERVAL_NOT_CHARGING_SCREEN_ON;
                intervalReason = "not_charging_screen_on";
            } else {
                shouldUpdateNotification = false; // 非充电 + 关屏 获取数据但不更新通知
                nextUpdateInterval = getNotChargingScreenOffInterval();
                intervalReason = stateInfo.isIdle() ? "not_charging_screen_off_idle"
                    : (isInScreenOffActiveWindow() ? "not_charging_screen_off_active_window"
                    : "not_charging_screen_off_stable");
            }
        }

        Log.d(TAG, "调度策略: reason=" + intervalReason + ", shouldNotify=" + shouldUpdateNotification
            + ", state=" + stateInfo + ", screenOffAge=" + getScreenOffAgeMs());

        BatteryInfo currentInfo = batteryInfoManager.getCurrentBatteryInfo();
        if (currentInfo == null) {
            persistHeartbeat("batteryInfo:null");
            scheduleRecoveryCheck("batteryInfo:null");
            return nextUpdateInterval;
        }

        // 更新当前会话缓存
        chargeHistoryManager.updateCurrentSession(currentInfo, stateInfo);

        persistCurrentState("updateBatteryInfo");
        persistHeartbeat("updateBatteryInfo");
        scheduleRecoveryCheck("updateBatteryInfo");

        // 根据策略决定是否更新通知
        if (shouldUpdateNotification) {
            notificationManager.notify(NOTIFICATION_ID, createNotification(currentInfo));
        }

        return nextUpdateInterval;
    }

    private long getNotChargingScreenOffInterval() {
        if (stateInfo.isIdle()) {
            return DATA_FETCH_INTERVAL_NOT_CHARGING_SCREEN_OFF_STABLE;
        }
        if (isInScreenOffActiveWindow()) {
            return DATA_FETCH_INTERVAL_NOT_CHARGING_SCREEN_OFF_ACTIVE;
        }
        return DATA_FETCH_INTERVAL_NOT_CHARGING_SCREEN_OFF_STABLE;
    }

    private boolean isInScreenOffActiveWindow() {
        long screenOffAge = getScreenOffAgeMs();
        return screenOffAge >= 0 && screenOffAge < SCREEN_OFF_ACTIVE_WINDOW_MS;
    }

    private long getScreenOffAgeMs() {
        if (lastScreenOffTimestamp <= 0 || stateInfo == null || stateInfo.isScreenOn()) {
            return -1L;
        }
        return System.currentTimeMillis() - lastScreenOffTimestamp;
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

    public static Intent createStartIntent(Context context, String startSource) {
        Intent intent = new Intent(context, BatteryMonitorService.class);
        intent.putExtra(EXTRA_START_SOURCE, startSource);
        return intent;
    }

    public static void startServiceCompat(Context context, String startSource) {
        Intent serviceIntent = createStartIntent(context, startSource);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }

    public static long getLastHeartbeat(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return preferences.getLong(KEY_LAST_HEARTBEAT, -1L);
    }

    public static boolean isServiceConsideredHealthy(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long lastHeartbeat = preferences.getLong(KEY_LAST_HEARTBEAT, -1L);
        boolean running = preferences.getBoolean(KEY_SERVICE_RUNNING, false);
        long age = lastHeartbeat > 0 ? System.currentTimeMillis() - lastHeartbeat : Long.MAX_VALUE;
        return running && age <= HEARTBEAT_STALE_THRESHOLD_MS;
    }

    private String getStartSource(Intent intent) {
        if (intent == null) {
            return "system_restart";
        }
        String startSource = intent.getStringExtra(EXTRA_START_SOURCE);
        return startSource != null ? startSource : "unknown";
    }

    private void persistStartSource(String startSource) {
        servicePrefs.edit()
            .putString(KEY_LAST_START_SOURCE, startSource)
            .apply();
    }

    private void markServiceRunning(boolean running) {
        servicePrefs.edit()
            .putBoolean(KEY_SERVICE_RUNNING, running)
            .apply();
    }

    private void persistHeartbeat(String reason) {
        long now = System.currentTimeMillis();
        servicePrefs.edit()
            .putLong(KEY_LAST_HEARTBEAT, now)
            .putBoolean(KEY_SERVICE_RUNNING, true)
            .apply();
        Log.d(TAG, "Persist heartbeat, reason=" + reason + ", ts=" + now);
    }

    private void persistCurrentState(String reason) {
        if (stateInfo == null) {
            return;
        }
        long now = System.currentTimeMillis();
        servicePrefs.edit()
            .putBoolean(KEY_LAST_SCREEN_ON, stateInfo.isScreenOn())
            .putBoolean(KEY_LAST_IDLE, stateInfo.isIdle())
            .putBoolean(KEY_LAST_CHARGING, stateInfo.isCharging())
            .putLong(KEY_LAST_STATE_UPDATE, now)
            .apply();
        Log.d(TAG, "Persist state, reason=" + reason + ", state=" + stateInfo);
    }

    private void logRecoveredState() {
        boolean hasLastState = servicePrefs.contains(KEY_LAST_STATE_UPDATE);
        if (!hasLastState) {
            Log.d(TAG, "No persisted state found for recovery");
            return;
        }

        long lastHeartbeat = servicePrefs.getLong(KEY_LAST_HEARTBEAT, -1L);
        long heartbeatAge = lastHeartbeat > 0 ? System.currentTimeMillis() - lastHeartbeat : -1L;
        boolean lastScreenOn = servicePrefs.getBoolean(KEY_LAST_SCREEN_ON, stateInfo.isScreenOn());
        boolean lastIdle = servicePrefs.getBoolean(KEY_LAST_IDLE, stateInfo.isIdle());
        boolean lastCharging = servicePrefs.getBoolean(KEY_LAST_CHARGING, stateInfo.isCharging());
        String lastStartSource = servicePrefs.getString(KEY_LAST_START_SOURCE, "unknown");

        Log.i(TAG, "Recovered persisted state: heartbeatAge=" + heartbeatAge
            + ", lastScreenOn=" + lastScreenOn
            + ", lastIdle=" + lastIdle
            + ", lastCharging=" + lastCharging
            + ", lastStartSource=" + lastStartSource
            + ", currentState=" + stateInfo);
    }

    private void scheduleRecoveryCheck(String reason) {
        if (alarmManager == null) {
            return;
        }

        long triggerAtMillis = SystemClock.elapsedRealtime() + RECOVERY_CHECK_INTERVAL_MS;
        PendingIntent pendingIntent = createRecoveryPendingIntent();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent);
        } else {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent);
        }

        servicePrefs.edit().putLong(KEY_LAST_ALARM_AT, System.currentTimeMillis()).apply();
        Log.d(TAG, "Schedule recovery check, reason=" + reason + ", triggerAt=" + triggerAtMillis);
    }

    private PendingIntent createRecoveryPendingIntent() {
        Intent recoveryIntent = new Intent(this, com.upo.batteryassistant.receiver.ServiceRecoveryReceiver.class);
        recoveryIntent.setAction(ACTION_RECOVERY_CHECK);
        return PendingIntent.getBroadcast(
            this,
            1001,
            recoveryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}

