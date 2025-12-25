package com.upo.batteryassistant.manager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import com.upo.batteryassistant.data.BatteryInfo;
import com.upo.batteryassistant.util.BatterySysfsReader;
import com.upo.batteryassistant.util.RootUtil;
import com.upo.batteryassistant.service.BatteryServiceConnector;
import com.upo.batteryassistant.service.BatteryData;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 电池信息管理器
 * 负责获取和管理电池信息
 */
public class BatteryInfoManager {
    private static BatteryInfoManager instance;
    private Context context;
    private BatteryInfoListener listener;
    private BroadcastReceiver batteryReceiver;
    private BatteryServiceConnector serviceConnector;

    /**
     * 电池信息更新监听器
     */
    public interface BatteryInfoListener {
        void onBatteryInfoChanged(BatteryInfo batteryInfo);
    }

    private BatteryInfoManager(Context context) {
        this.context = context.getApplicationContext();
        this.serviceConnector = new BatteryServiceConnector();
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

        // ========== Magic Service 高级信息填充 ==========
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
     * 使用 Magic Service 从 /sys/class/power_supply 读取高级信息并填充到 BatteryInfo
     */
    private void fillAdvancedInfoIfRootAvailable(BatteryInfo info) {
        if (info == null) {
            return;
        }
        
        android.util.Log.d("BatteryInfoManager", "Attempting to get advanced battery info from Magic Service");
        
        // 尝试使用Magic Service获取高级信息
        try {
            // 首先运行详细诊断
            String diagnostics = serviceConnector.testConnectionWithDiagnostics();
            android.util.Log.d("BatteryInfoManager", "Socket diagnostics:\n" + diagnostics);
            
            // 连接到Magic Service
            android.util.Log.d("BatteryInfoManager", "Attempting to connect to Magic Service...");
            if (serviceConnector.connect()) {
                android.util.Log.d("BatteryInfoManager", "Connected to Magic Service successfully, requesting battery status...");
                
                BatteryData data = serviceConnector.getBatteryStatus().get(5, TimeUnit.SECONDS);
                if (data != null) {
                    android.util.Log.d("BatteryInfoManager", "Received battery data from Magic Service");
                    // 填充高级电池信息
                    info.setAdvBattCapacity(data.getCapacity());
                    info.setAdvBattTempDeciC(data.getTemperature());
                    info.setAdvBattVoltageNowUv(data.getVoltageNow());
                    info.setAdvBattCurrentNowUa((int) data.getCurrentNow());
                    info.setAdvBattStatusText(data.getStatus());
                    info.setAdvBattHealthText(data.getHealth());
                    info.setAdvBattTechnology(data.getTechnology());
                    info.setAdvBattChargeCounterUah(data.getChargeCounter());
                    info.setAdvBattChargeFullUah(data.getChargeFull());
                    info.setAdvBattCycleCount(data.getCycleCount());
                    info.setAdvBattChargeCtrlStartThr(data.getChargeStartThreshold());
                    info.setAdvBattChargeCtrlEndThr(data.getChargeEndThreshold());
                    info.setAdvBattChargeCtrlLimit(data.getChargeLimit());
                    
                    // USB信息
                    info.setAdvUsbOnline(data.isUsbOnline());
                    info.setAdvUsbVoltageNowUv(data.getUsbVoltageNow());
                    info.setAdvUsbCurrentNowUa((int) data.getUsbCurrentNow());
                    
                    // 无线充电信息
                    info.setAdvWlsOnline(data.isWirelessOnline());
                    info.setAdvWlsVoltageNowUv(data.getWirelessVoltageNow());
                    info.setAdvWlsCurrentNowUa((int) data.getWirelessCurrentNow());
                    
                    android.util.Log.i("BatteryInfoManager", "Successfully filled advanced battery info from Magic Service");
                    return;
                } else {
                    android.util.Log.w("BatteryInfoManager", "Magic Service returned null data");
                }
            } else {
                android.util.Log.w("BatteryInfoManager", "Failed to connect to Magic Service");
            }
        } catch (java.util.concurrent.TimeoutException e) {
            android.util.Log.e("BatteryInfoManager", "Magic Service request timed out", e);
        } catch (Exception e) {
            // Magic Service不可用时，回退到原有RootUtil
            android.util.Log.w("BatteryInfoManager", "Magic Service unavailable, falling back to RootUtil", e);
        }
        
        // 降级到原有RootUtil方案
        if (!RootUtil.isRootAvailable()) {
            return;
        }
        BatterySysfsReader.fillBatteryAdvancedFields(info);
        BatterySysfsReader.fillUsbAdvancedFields(info);
        BatterySysfsReader.fillWirelessAdvancedFields(info);
    }
    
    /**
     * 设置充电阈值
     */
    public CompletableFuture<Boolean> setChargeThreshold(int startThreshold, int endThreshold) {
        return serviceConnector.setChargeThreshold(startThreshold, endThreshold);
    }
    
    /**
     * 设置充电限制
     */
    public CompletableFuture<Boolean> setChargeLimit(int limit) {
        return serviceConnector.setChargeLimit(limit);
    }
    
    /**
     * 启用/禁用充电
     */
    public CompletableFuture<Boolean> enableCharging(boolean enable) {
        return serviceConnector.enableCharging(enable);
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        if (serviceConnector != null) {
            serviceConnector.cleanup();
        }
    }
}