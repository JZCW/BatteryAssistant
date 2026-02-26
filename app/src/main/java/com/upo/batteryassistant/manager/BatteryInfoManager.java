package com.upo.batteryassistant.manager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import com.upo.batteryassistant.data.BatteryInfo;
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
        BatteryInfo info = new BatteryInfo();

        fillBasicInfo(info);



        // ========== Magisk Service 高级信息填充 ==========
        // fillAdvancedInfoIfRootAvailable(info);

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
     * 使用系统API获取基础电池信息
     * @return true表示获取成功，false表示获取失败
     */
    private boolean fillBasicInfo(BatteryInfo info) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);
        if (batteryStatus == null) {
            return false;
        }

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        if (level >= 0 && scale > 0) {
            // 计算电量百分比
            info.setLevel((int) (level*100 / scale));
        } else if (level >= 0) {
            // 如果scale无效，直接使用level值
            info.setLevel(level);
        }

        // 温度（0.1°C）
        info.setTemperature(batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1));

        // 电压（毫伏）
        info.setVoltage(batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1));

        // 健康状态
        info.setHealthApi(batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1));

        // 充电状态
        info.setStatus(batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1));

        // // 插电方式
        // info.setPlugged(batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1));

        // 循环次数
        info.setCycleCount(batteryStatus.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1));

        // ========== 使用BatteryManager获取高级属性 ==========
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager != null) {
            // 当前电流（毫安）
            int currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            if (currentNow != Integer.MIN_VALUE) {
                info.setCurrent((int) (currentNow/1000.0f));
            }

            // 平均电流（毫安）
            int currentAverage = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
            if (currentAverage != Integer.MIN_VALUE) {
                info.setCurrentAverage((int) (currentAverage/1000.0f));
            }

            // 充电计数器（毫安时）
            long chargeCounter = batteryManager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
            if (chargeCounter != Long.MIN_VALUE) {
                info.setChargeCounter((int) (chargeCounter/1000.0f));
            }

            // 剩余充电时间
            long chargeTimeRemaining = batteryManager.computeChargeTimeRemaining();
            info.setChargeTimeRemaining(chargeTimeRemaining);
        }

        return true;
    }

    /**
     * 从 Magisk Service 读取信息并填充到 BatteryInfo
     * @return true表示获取成功，false表示获取失败
     */
    private boolean fillAdvancedInfoIfRootAvailable(BatteryInfo info) {
        android.util.Log.d("BatteryInfoManager", "Attempting to get advanced battery info from Magisk Service");
        
        // 尝试使用Magisk Service获取高级信息
        try {
            // 首先运行详细诊断
            // String diagnostics = serviceConnector.testConnectionWithDiagnostics();
            // android.util.Log.d("BatteryInfoManager", "Socket diagnostics:\n" + diagnostics);
            
            // 连接到Magisk Service
            android.util.Log.d("BatteryInfoManager", "Attempting to connect to Magisk Service...");
            if (serviceConnector.connect()) {
                android.util.Log.d("BatteryInfoManager", "Connected to Magisk Service successfully, requesting battery status...");
                
                BatteryData data = serviceConnector.getBatteryStatus().get(2, TimeUnit.SECONDS);
                if (data != null) {
                    android.util.Log.d("BatteryInfoManager", "Received battery data from Magisk Service");
                    // // 填充高级电池信息
                    // info.setAdvBattCapacity(data.getCapacity());
                    // info.setAdvBattTempDeciC(data.getTemperature());
                    // info.setAdvBattVoltageNowUv(data.getVoltageNow());
                    // info.setAdvBattCurrentNowUa((int) data.getCurrentNow());
                    // info.setAdvBattStatusText(data.getStatus());
                    // info.setAdvBattHealthText(data.getHealth());
                    // info.setAdvBattTechnology(data.getTechnology());
                    // info.setAdvBattChargeCounterUah(data.getChargeCounter());
                    // info.setAdvBattChargeFullUah(data.getChargeFull());
                    // info.setAdvBattCycleCount(data.getCycleCount());
                    // info.setAdvBattChargeCtrlStartThr(data.getChargeStartThreshold());
                    // info.setAdvBattChargeCtrlEndThr(data.getChargeEndThreshold());
                    // info.setAdvBattChargeCtrlLimit(data.getChargeLimit());
                    
                    // // USB信息
                    // info.setAdvUsbOnline(data.isUsbOnline());
                    // info.setAdvUsbVoltageNowUv(data.getUsbVoltageNow());
                    // info.setAdvUsbCurrentNowUa((int) data.getUsbCurrentNow());
                    
                    // // 无线充电信息
                    // info.setAdvWlsOnline(data.isWirelessOnline());
                    // info.setAdvWlsVoltageNowUv(data.getWirelessVoltageNow());
                    // info.setAdvWlsCurrentNowUa((int) data.getWirelessCurrentNow());
                    
                    android.util.Log.i("BatteryInfoManager", "Successfully filled advanced battery info from Magisk Service");
                    return true;
                } else {
                    android.util.Log.w("BatteryInfoManager", "Magisk Service returned null data");
                }
            } else {
                android.util.Log.w("BatteryInfoManager", "Failed to connect to Magisk Service");
            }
        } catch (java.util.concurrent.TimeoutException e) {
            android.util.Log.e("BatteryInfoManager", "Magisk Service request timed out");
        } catch (Exception e) {
            android.util.Log.e("BatteryInfoManager", "Magisk Service unavailable", e);
        }
        
        return false;
    }
    
    /**
     * 设置充电限制
     */
    public CompletableFuture<Boolean> setChargeLimit(int limit) {
        return serviceConnector.setChargeLimit(limit);
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