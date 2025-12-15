package com.upo.batteryassistant.manager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import com.upo.batteryassistant.data.BatteryInfo;
import com.upo.batteryassistant.util.BatterySysfsReader;
import com.upo.batteryassistant.util.RootUtil;

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

        // ========== 从Intent EXTRA获取基础信息 ==========
        // 电量级别和最大值
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level >= 0 && scale > 0) {
            // 计算电量百分比
            info.setLevel((int) (level * 100.0f / scale));
        } else if (level >= 0) {
            // 如果scale无效，直接使用level值
            info.setLevel(level);
        } else {
            info.setLevel(-1);
        }

        // 电压（毫伏）
        info.setVoltage(batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1));

        // 温度（0.1°C）
        info.setTemperature(batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1));

        // 充电状态
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        info.setStatus(status);

        // 健康状态
        info.setHealth(batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1));

        // 插电方式
        info.setPlugged(batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1));

        // 是否低电量
        info.setLow(batteryStatus.getBooleanExtra(BatteryManager.EXTRA_BATTERY_LOW, false));

        // 容量级别
        // int EXTRA_CAPACITY_LEVEL

        // 循环次数
        info.setCycleCount(batteryStatus.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1));

        // int EXTRA_ICON_SMALL

        // ========== 使用BatteryManager获取高级属性 ==========
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager != null) {
            // 当前电流（微安）
            int currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (currentNow != Integer.MIN_VALUE) {
                info.setCurrent(currentNow); // 微安，需要转换为毫安显示
            }

            // 平均电流（微安）
            int currentAverage = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
            if (currentAverage != Integer.MIN_VALUE) {
                info.setCurrentAverage(currentAverage); // 微安，需要转换为毫安显示
            }

            // 充电计数器（微安时）
            long chargeCounter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            if (chargeCounter != Long.MIN_VALUE) {
                info.setChargeCounter(chargeCounter);
            }

            // 剩余能量（纳瓦时）
            // long BATTERY_PROPERTY_ENERGY_COUNTER //多数设备不可用

            // 剩余充电时间 //TODO 实现自己的计算方法
            long chargeTimeRemaining = batteryManager.computeChargeTimeRemaining();
            info.setChargeTimeRemaining(chargeTimeRemaining);

            // 是否正在充电
            // boolean isCharging  始终返回false
        }

        // ========== Root 高级信息填充 ==========
        fillAdvancedInfoIfRootAvailable(info);

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

    /**
     * 使用 root 从 /sys/class/power_supply 读取高级信息并填充到 BatteryInfo
     */
    private void fillAdvancedInfoIfRootAvailable(BatteryInfo info) {
        if (info == null) {
            return;
        }
        if (!RootUtil.isRootAvailable()) {
            return;
        }
        BatterySysfsReader.fillBatteryAdvancedFields(info);
        BatterySysfsReader.fillUsbAdvancedFields(info);
        BatterySysfsReader.fillWirelessAdvancedFields(info);
    }
}

