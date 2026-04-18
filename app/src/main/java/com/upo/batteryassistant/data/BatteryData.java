package com.upo.batteryassistant.data;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 电池数据模型
 * 从Magic Daemon获取的电池状态数据
 */
public class BatteryData {
    /** 无效值标记，超出所有合理取值范围 */
    public static final int INVALID_VALUE = 65536;

    private int version;
    private long timestamp; // 时间戳
    private int capacity;   // 0-100, 电池电量百分比
    private int voltage_now;           // 当前电池电压(mV)  // 和adc读数不一致？
    private int voltage_max;           // 最大电池电压(mV)
    private int voltage_ocv;           // 电池开路电压(mV) //最低电压？
    private int current_now;           // 当前电池电流(mA) // 同max？
    private int current_avg;           // 平均电池电流(mA)
    private int temp_battery;          // 电池温度(0.1°C)
    private int health;                // 电池健康状态 0-100
    private String status_str;    // 电池状态文本 "Charging", "Discharging", "Full"
    private String charge_type_str; // 充电类型文本 "Fast", "Standard", "N/A"
    private int charge_counter;        // charge counter in mAh  // 剩余电量
    private int cycle_count;           // 电池循环次数
    private int charge_full;           // 实际充满容量 in mAh
    private int charge_design;         // 设计充满容量 in mAh
    private int usb_online;             // 0 or 1
    private int usb_voltage_now;        // voltage in mV
    private int usb_voltage_max;        // max voltage in mV
    private int in_current_now;        // current in mA // usb与wls相同数据
    private int usb_current_max;        // max current in mA
    private String usb_type;       // [Unknown] SDP DCP CDP ACA C PD PD_DRP PD_PPS BrickID
    private int wireless_online;         // 0 or 1
    private int wireless_voltage_now;    // voltage in mV
    private int wireless_voltage_max;    // max voltage in mV
    private int wireless_current_max;    // max current in mA
    private String wireless_type;       // [Unknown] BPP
    private int scenario_fcc;           // 场景快速充电电流(mA)
    private int nt_abnormal_status;     // 异常状态

    public BatteryData() {
        // 默认构造函数
    }
    
    public static BatteryData fromJson(JSONObject json) throws JSONException {
        BatteryData data = new BatteryData();

        data.version = json.optInt("version", 1);

        data.timestamp = json.optLong("timestamp", 0L);
        data.capacity = json.optInt("capacity", -1);
        data.voltage_now = json.optInt("voltage_now", -1);
        data.voltage_max = json.optInt("voltage_max", -1);
        data.voltage_ocv = json.optInt("voltage_ocv", -1);
        data.current_now = json.optInt("current_now", INVALID_VALUE);
        data.current_avg = json.optInt("current_avg", INVALID_VALUE);
        data.temp_battery = json.optInt("temp_battery", -1);
        data.health = json.optInt("health", -1);
        data.status_str = json.optString("status_str", "");
        data.charge_type_str = json.optString("charge_type_str", "");
        data.charge_counter = json.optInt("charge_counter", -1);
        data.cycle_count = json.optInt("cycle_count", -1);
        data.charge_full = json.optInt("charge_full", -1);
        data.charge_design = json.optInt("charge_design", -1);
        data.usb_online = json.optInt("usb_online", -1);
        data.usb_voltage_now = json.optInt("usb_voltage_now", -1);
        data.usb_voltage_max = json.optInt("usb_voltage_max", -1);
        data.in_current_now = json.optInt("in_current_now", INVALID_VALUE);
        data.usb_current_max = json.optInt("usb_current_max", INVALID_VALUE);
        data.usb_type = json.optString("usb_type", "");
        data.wireless_online = json.optInt("wireless_online", -1);
        data.wireless_voltage_now = json.optInt("wireless_voltage_now", -1);
        data.wireless_voltage_max = json.optInt("wireless_voltage_max", -1);
        data.wireless_current_max = json.optInt("wireless_current_max", INVALID_VALUE);
        data.wireless_type = json.optString("wireless_type", "");
        data.scenario_fcc = json.optInt("scenario_fcc", -1);
        data.nt_abnormal_status = json.optInt("nt_abnormal_status", -1);
        
        return data;
    }
    
    public long getTimestamp() {
        return timestamp;
    }

    // 电池相关getter方法
    public int getCapacity() {
        return capacity;
    }

    public int getVoltageNow() {
        return voltage_now;
    }

        // data.voltage_max = json.getInt("voltage_max");
        // data.voltage_ocv = json.getInt("voltage_ocv");

    public int getCurrentNow() {
        return getInvalidValue(current_now);
    }
    
    public int getCurrentAverage() {
        return getInvalidValue(current_avg);
    }

    public int getTempBattery() {
        return temp_battery;
    }

    public int getHealth() {
        return health;
    }

    public int getStatus() {
        switch (status_str) {
            case "Charging":
                return android.os.BatteryManager.BATTERY_STATUS_CHARGING;
            case "Discharging":
                return android.os.BatteryManager.BATTERY_STATUS_DISCHARGING;
            case "Full":
                return android.os.BatteryManager.BATTERY_STATUS_FULL;
            default:
                return android.os.BatteryManager.BATTERY_STATUS_UNKNOWN;
        }
    }

    // data.charge_type_str = json.getString("charge_type_str");

    public int getChargeCounter() {
        return charge_counter;
    }

    public int getCycleCount() {
        return cycle_count;
    }

    public int getChargeFull() {
        return charge_full;
    }

    public int getChargeDesign() {
        return charge_design;
    }

    public boolean isUsbOnline() {
        return usb_online == 1;
    }

    public int getUsbVoltageNow() {
        return usb_voltage_now;
    }

    public int getUsbVoltageMax() {
        return usb_voltage_max;
    }

    public int getInCurrentNow() {
        return getInvalidValue(in_current_now);
    }

    public int getUsbCurrentMax() {
        return getInvalidValue(usb_current_max);
    }

        // data.usb_type = json.getString("usb_type");

    public boolean isWirelessOnline() {
        return wireless_online == 1;
    }

    public int getWirelessVoltageNow() {
        return wireless_voltage_now;
    }

    public int getWirelessVoltageMax() {
        return wireless_voltage_max;
    }

    public int getWirelessCurrentMax() {
        return getInvalidValue(wireless_current_max);
    }

        // data.wireless_type = json.getString("wireless_type");

    public int getScenarioFcc() {
        return scenario_fcc;
    }

        // data.nt_abnormal_status = json.getInt("nt_abnormal_status");
    
    @Override
    public String toString() {
        return "BatteryData{" +
                "timestamp=" + timestamp +
                '}';
    }

    private int getInvalidValue(int value) {
        return (value >= INVALID_VALUE) ? Integer.MIN_VALUE : value;
    }
}