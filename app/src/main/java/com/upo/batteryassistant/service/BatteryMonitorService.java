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
import androidx.core.app.NotificationCompat;
import com.upo.batteryassistant.R;
import com.upo.batteryassistant.data.BatteryInfo;
import com.upo.batteryassistant.manager.BatteryInfoManager;
import com.upo.batteryassistant.manager.ChargeHistoryManager;
import com.upo.batteryassistant.ui.MainActivity;

/**
 * 电池监控服务（前台服务）
 * 负责在后台持续监控电池状态并显示通知
 */
public class BatteryMonitorService extends Service {
    private static final String CHANNEL_ID = "BatteryMonitorChannel";
    private static final int NOTIFICATION_ID = 1;
    
    private BatteryInfoManager batteryInfoManager;
    private ChargeHistoryManager chargeHistoryManager;
    private BroadcastReceiver batteryReceiver;
    private BroadcastReceiver powerReceiver;
    private BroadcastReceiver screenStateReceiver;
    private NotificationManager notificationManager;
    private BatteryInfo currentBatteryInfo;
    private Handler updateHandler;
    private Runnable updateRunnable;
    private boolean isScreenOn = true;
    private long lastNotificationUpdateTime = 0;
    private BatteryInfo lastNotificationInfo;
    
    // 更新间隔配置
    private static final long NOTIFICATION_UPDATE_INTERVAL_HIGH = 1000; // 屏幕亮起时 1秒
    private static final long NOTIFICATION_UPDATE_INTERVAL_LOW = 60000; // 息屏时 60秒

    @Override
    public void onCreate() {
        super.onCreate();
        
        batteryInfoManager = BatteryInfoManager.getInstance(this);
        chargeHistoryManager = ChargeHistoryManager.getInstance(this);
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        updateHandler = new Handler(Looper.getMainLooper());
        
        // 创建通知渠道（Android 8.0+）
        createNotificationChannel();
        
        // 初始化充放电历史管理器
        chargeHistoryManager.init();
        
        // 注册电池状态监听
        registerBatteryReceiver();
        
        // 注册充电器事件监听
        registerPowerReceiver();
        
        // 注册屏幕状态监听
        registerScreenStateReceiver();
        
        // 立即获取一次电池信息
        updateBatteryInfo();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification());
        return START_STICKY; // 服务被杀死后自动重启
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterBatteryReceiver();
        unregisterPowerReceiver();
        unregisterScreenStateReceiver();
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
     * 注册电池状态广播接收器
     */
    private void registerBatteryReceiver() {
        if (batteryReceiver != null) {
            return;
        }

        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                    updateBatteryInfo();
                }
            }
        };

        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, filter);
    }

    /**
     * 注销电池状态广播接收器
     */
    private void unregisterBatteryReceiver() {
        if (batteryReceiver != null) {
            try {
                unregisterReceiver(batteryReceiver);
                batteryReceiver = null;
            } catch (IllegalArgumentException e) {
                // 接收器未注册，忽略
            }
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
                    chargeHistoryManager.onPowerConnected();
                } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                    chargeHistoryManager.onPowerDisconnected();
                }
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
     * 停止通知更新
     */
    private void stopNotificationUpdate() {
        if (updateRunnable != null) {
            updateHandler.removeCallbacks(updateRunnable);
            updateRunnable = null;
        }
    }

    /**
     * 更新电池信息并刷新通知
     */
    private void updateBatteryInfo() {
        long currentTime = System.currentTimeMillis();
        long updateInterval = isScreenOn ? NOTIFICATION_UPDATE_INTERVAL_HIGH : NOTIFICATION_UPDATE_INTERVAL_LOW;
        if (currentTime - lastNotificationUpdateTime >= updateInterval) {
        currentBatteryInfo = batteryInfoManager.getCurrentBatteryInfo();
            if (currentBatteryInfo != null) {
                lastNotificationUpdateTime = currentTime;
                notificationManager.notify(NOTIFICATION_ID, createNotification());
            }
        }
    }

    /**
     * 创建通知
     */
    private Notification createNotification() {
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

        if (currentBatteryInfo != null) {
            // 通知标题
            String title = String.format("电池: %d%%", currentBatteryInfo.getLevel());
            
            // 通知内容
            StringBuilder content = new StringBuilder();
            content.append(currentBatteryInfo.getStatusText());
            
            if (currentBatteryInfo.getVoltage() > 0) {
                content.append(" | ").append(String.format("%.2fV", currentBatteryInfo.getVoltageVolts()));
            }
            
            if (currentBatteryInfo.getTemperature() > 0) {
                content.append(" | ").append(String.format("%.1f°C", currentBatteryInfo.getTemperatureCelsius()));
            }
            
            // 如果正在充电，显示剩余充电时间
            if (currentBatteryInfo.isCharging() && currentBatteryInfo.getChargeTimeRemaining() >= 0) {
                content.append("\n").append("预计充满: ").append(currentBatteryInfo.getChargeTimeRemainingText());
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
}

