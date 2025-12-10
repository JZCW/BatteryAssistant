package com.upo.batteryassistant.manager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import com.upo.batteryassistant.data.BatteryInfo;

/**
 * 电池信息管理器
 * 负责获取和管理电池信息
 */
public class BatteryInfoManager {
    private static BatteryInfoManager instance;
    private Context context;
    private BatteryInfoListener listener;
    private BroadcastReceiver batteryReceiver;

    /**
     * 电池信息更新监听器
     */
    public interface BatteryInfoListener {
        void onBatteryInfoChanged(BatteryInfo batteryInfo);
    }

    private BatteryInfoManager(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 获取单例实例
     */
    public static BatteryInfoManager getInstance(Context context) {
        if (instance == null) {
            synchronized (BatteryInfoManager.class) {
                if (instance == null) {
                    instance = new BatteryInfoManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * 设置电池信息更新监听器
     */
    public void setListener(BatteryInfoListener listener) {
        this.listener = listener;
    }

    /**
     * 获取当前电池信息
     */
    public BatteryInfo getCurrentBatteryInfo() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);

        if (batteryStatus == null) {
            return null;
        }

        BatteryInfo info = new BatteryInfo();

        // 电量百分比
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level >= 0 && scale > 0) {
            info.setLevel((int) (level * 100.0f / scale));
        } else {
            info.setLevel(level);
        }

        // 电压（毫伏）
        info.setVoltage(batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1));

        // 温度（0.1°C）
        info.setTemperature(batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1));

        // 充电状态
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        info.setStatus(status);
        info.setCharging(status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL);

        // 健康状态
        info.setHealth(batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1));

        // 电池技术类型
        info.setTechnology(batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY));

        // 电流（需要root权限，暂时返回0）
        info.setCurrent(0);

        return info;
    }

    /**
     * 注册电池状态广播接收器
     */
    public void registerBatteryReceiver() {
        if (batteryReceiver != null) {
            return; // 已经注册
        }

        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                    BatteryInfo info = getCurrentBatteryInfo();
                    if (listener != null && info != null) {
                        listener.onBatteryInfoChanged(info);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        context.registerReceiver(batteryReceiver, filter);
    }

    /**
     * 注销电池状态广播接收器
     */
    public void unregisterBatteryReceiver() {
        if (batteryReceiver != null) {
            try {
                context.unregisterReceiver(batteryReceiver);
                batteryReceiver = null;
            } catch (IllegalArgumentException e) {
                // 接收器未注册，忽略
            }
        }
    }
}

