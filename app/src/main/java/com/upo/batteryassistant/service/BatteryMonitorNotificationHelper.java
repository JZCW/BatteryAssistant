package com.upo.batteryassistant.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.upo.batteryassistant.R;
import com.upo.batteryassistant.data.BatteryInfo;
import com.upo.batteryassistant.ui.MainActivity;

/**
 * 电池监控通知助手。
 * 统一封装通知渠道、通知构建和通知更新，便于后续扩展通知样式与交互。
 */
public class BatteryMonitorNotificationHelper {
    private static final String CHANNEL_ID = "BatteryMonitorChannel";
    private static final int NOTIFICATION_ID = 1;

    private final Context appContext;
    private final NotificationManager notificationManager;

    public BatteryMonitorNotificationHelper(Context context) {
        this.appContext = context.getApplicationContext();
        this.notificationManager = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public int getNotificationId() {
        return NOTIFICATION_ID;
    }

    public void ensureChannel() {
        if (notificationManager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            "电池监控",
            NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("显示电池状态信息");
        channel.setShowBadge(false);
        channel.enableLights(false);
        channel.enableVibration(false);
        notificationManager.createNotificationChannel(channel);
    }

    public void notifyBattery(BatteryInfo batteryInfo) {
        if (notificationManager == null) {
            return;
        }
        notificationManager.notify(NOTIFICATION_ID, createNotification(batteryInfo));
    }

    public Notification createNotification(BatteryInfo batteryInfo) {
        Intent intent = new Intent(appContext, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_battery_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .setAutoCancel(false);

        if (batteryInfo != null) {
            String title = String.format("电池: %d%%", batteryInfo.getLevel());

            StringBuilder content = new StringBuilder();
            content.append(batteryInfo.getStatusText());

            if (batteryInfo.getVoltage() > 0) {
                content.append(" | ").append(String.format("%.2fV", batteryInfo.getVoltageVolts()));
            }

            if (batteryInfo.getTemperature() > 0) {
                content.append(" | ").append(String.format("%.1f°C", batteryInfo.getTemperatureCelsius()));
            }

            if (batteryInfo.isCharging() && batteryInfo.getChargeTimeRemaining() >= 0) {
                content.append("\n").append("预计充满: ").append(batteryInfo.getChargeTimeRemainingText());
            }

            String contentText = content.toString();
            builder.setContentTitle(title)
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText));
        } else {
            builder.setContentTitle("电池监控")
                .setContentText("正在获取电池信息...");
        }

        return builder.build();
    }
}