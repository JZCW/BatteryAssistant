package com.upo.batteryassistant.manager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import com.upo.batteryassistant.data.BatteryData;
import com.upo.batteryassistant.data.BatteryInfo;
import com.upo.batteryassistant.service.BatteryServiceConnector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 电池信息管理器
 * 负责获取和管理电池信息
 */
public class BatteryInfoManager {
    private static BatteryInfoManager instance;
    private Context context;
    private BatteryServiceConnector serviceConnector;
    private BatteryInfo cache;

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
     * 获取当前电池信息
     * @param forceRefresh 是否强制刷新
     */
    public BatteryInfo getCurrentBatteryInfo() {
        return getCurrentBatteryInfo(false);
    }
    public BatteryInfo getCurrentBatteryInfo(boolean forceRefresh) {
        if ((!forceRefresh) && (cache != null) && ((System.currentTimeMillis() - cache.getTimestamp()) < 2000)) {
            return cache;
        }

        BatteryInfo info = new BatteryInfo();

        boolean isGetData = false;
        if (serviceConnector.isProxyRunning()) {
            isGetData = fillAdvancedInfoIfRootAvailable(info);
        }

        // 如果没有成功从daemon获取数据，则使用系统API获取基础信息
        if (!isGetData) {
            fillBasicInfo(info);
        }

        cache = info;
        return cache;
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

        info.setTimestamp(System.currentTimeMillis());

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
     * 从 daemon 读取信息并填充到 BatteryInfo
     * @return true表示获取成功，false表示获取失败
     */
    private boolean fillAdvancedInfoIfRootAvailable(BatteryInfo info) {
        BatteryData data = null;
        try {
            data = serviceConnector.getBatteryStatus().get(1, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            android.util.Log.e("BatteryInfoManager", "Magisk Service request timed out");
        } catch (Exception e) {
            android.util.Log.e("BatteryInfoManager", "Magisk Service unavailable", e);
        }

        if (data != null) {
            info.setTimestamp(data.getTimestamp());
            info.setLevel(data.getCapacity());
            info.setTemperature(data.getTempBattery());
            info.setVoltage(data.getVoltageNow());
            info.setCurrent(data.getCurrentNow());
            info.setCurrentAverage(data.getCurrentAverage());
            info.setHealth(data.getHealth());
            info.setStatus(data.getStatus());
            info.setChargeCounter(data.getChargeCounter());
            info.setCycleCount(data.getCycleCount());
            info.setFullCapacity(data.getChargeFull());
            info.setDesignCapacity(data.getChargeDesign());
            info.setUsbOnline(data.isUsbOnline());
            info.setUsbVoltageNow(data.getUsbVoltageNow());
            info.setUsbCurrentMax(data.getUsbCurrentMax());
            info.setUsbCurrentMax(data.getUsbCurrentMax());
            info.setWirelessOnline(data.isWirelessOnline());
            info.setWirelessVoltageNow(data.getWirelessVoltageNow());
            info.setWirelessVoltageMax(data.getWirelessVoltageMax());
            info.setWirelessCurrentMax(data.getWirelessCurrentMax());
            info.setInCurrentNow(data.getCurrentNow());
            info.setScenarioFcc(data.getScenarioFcc());

            // TODO
            //基础信息有的
            // private int health_api = -1;           // API健康状态
            // private long chargeTimeRemaining = -1; // 剩余充电时间，单位：毫秒（-1表示无法计算）
            
            //高级信息未使用的
            // private int version;
            // private int voltage_max;           // 最大电池电压(μV)
            // private int voltage_ocv;           // 电池开路电压(μV) //最低电压？
            // private String charge_type_str; // 充电类型文本 "Fast", "Standard", "N/A"
            // private String usb_type;       // [Unknown] SDP DCP CDP ACA C PD PD_DRP PD_PPS BrickID
            // private String wireless_type;       // [Unknown] BPP
            // private int nt_abnormal_status;     // 异常状态

            return true;
        }

        return false;
    }
    
    /**
     * 确保 bridge 守护进程已启动
     * 无 root 环境会静默跳过
     */
    public void ensureBridgeStarted() {
        if (serviceConnector != null) {
            serviceConnector.ensureBridgeStarted();
        }
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