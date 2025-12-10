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

        // ========== 从Intent EXTRA获取基础信息 ==========
        
        // 电量级别和最大值
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        info.setScale(scale);
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
        info.setCharging(status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL);

        // 健康状态
        info.setHealth(batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1));

        // 插电方式
        info.setPlugged(batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1));

        // 电池是否存在
        info.setPresent(batteryStatus.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false));

        // 电池技术类型
        info.setTechnology(batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY));

        // 是否低电量（API 28+）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.setLow(batteryStatus.getBooleanExtra(BatteryManager.EXTRA_BATTERY_LOW, false));
        }

        // 容量级别（API 36+）
        if (android.os.Build.VERSION.SDK_INT >= 36) {
            info.setCapacityLevel(batteryStatus.getIntExtra(BatteryManager.EXTRA_CAPACITY_LEVEL, 
                    BatteryManager.BATTERY_CAPACITY_LEVEL_UNKNOWN));
        }

        // 充电状态（API 34+）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.setChargingStatus(batteryStatus.getIntExtra(BatteryManager.EXTRA_CHARGING_STATUS, -1));
        }

        // 循环次数（API 34+）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.setCycleCount(batteryStatus.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1));
        }

        // ========== 使用BatteryManager获取高级属性（API 21+） ==========
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            // 电量百分比
            int capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (capacity != Integer.MIN_VALUE) {
                info.setCapacity(capacity);
            }

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
            long energyCounter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);
            if (energyCounter != Long.MIN_VALUE) {
                info.setEnergyCounter(energyCounter);
            }

            // 剩余充电时间（API 28+）
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                long chargeTimeRemaining = batteryManager.computeChargeTimeRemaining();
                info.setChargeTimeRemaining(chargeTimeRemaining);
            }

            // 是否正在充电（API 23+）
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                info.setCharging(batteryManager.isCharging());
            }
        }

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

