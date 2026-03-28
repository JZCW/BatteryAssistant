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
    private int version;
    private long timestamp; // 时间戳
    private int capacity;   // 0-100, 电池电量百分比
    private int voltage_now;           // 当前电池电压(μV)  // 和adc读数不一致？
    private int voltage_max;           // 最大电池电压(μV)
    private int voltage_ocv;           // 电池开路电压(μV) //最低电压？
    private int current_now;           // 当前电池电流(μA) // 同max？
    private int current_avg;           // 平均电池电流(μA)
    private int temp_battery;          // 电池温度(0.1°C)
    private int health;                // 电池健康状态 0-100
    private String status_str;    // 电池状态文本 "Charging", "Discharging", "Full"
    private String charge_type_str; // 充电类型文本 "Fast", "Standard", "N/A"
    private int charge_counter;        // charge counter in μAh  // 剩余电量
    private int cycle_count;           // 电池循环次数
    private int charge_full;           // 实际充满容量 in μAh
    private int charge_design;         // 设计充满容量 in μAh
    private int usb_online;             // 0 or 1
    private int usb_voltage_now;        // voltage in μV
    private int usb_voltage_max;        // max voltage in μV
    private int in_current_now;        // current in μA // usb与wls相同数据
    private int usb_current_max;        // max current in μA
    private String usb_type;       // [Unknown] SDP DCP CDP ACA C PD PD_DRP PD_PPS BrickID
    private int wireless_online;         // 0 or 1
    private int wireless_voltage_now;    // voltage in μV
    private int wireless_voltage_max;    // max voltage in μV
    private int wireless_current_max;    // max current in μA
    private String wireless_type;       // [Unknown] BPP
    private int scenario_fcc;           // 场景快速充电电流(μA)
    private int nt_abnormal_status;     // 异常状态
    
    public BatteryData() {
        // 默认构造函数
    }
    
    public static BatteryData fromJson(JSONObject json) throws JSONException {
        BatteryData data = new BatteryData();

        data.version = json.getInt("version");
        
        data.timestamp = json.getLong("timestamp");
        data.capacity = json.getInt("capacity");
        data.voltage_now = json.getInt("voltage_now");
        data.voltage_max = json.getInt("voltage_max");
        data.voltage_ocv = json.getInt("voltage_ocv");
        data.current_now = json.getInt("current_now");
        data.current_avg = json.getInt("current_avg");
        data.temp_battery = json.getInt("temp_battery");
        data.health = json.getInt("health");
        data.status_str = json.getString("status_str");
        data.charge_type_str = json.getString("charge_type_str");
        data.charge_counter = json.getInt("charge_counter");
        data.cycle_count = json.getInt("cycle_count");
        data.charge_full = json.getInt("charge_full");
        data.charge_design = json.getInt("charge_design");
        data.usb_online = json.getInt("usb_online");
        data.usb_voltage_now = json.getInt("usb_voltage_now");
        data.usb_voltage_max = json.getInt("usb_voltage_max");
        data.in_current_now = json.getInt("in_current_now");
        data.usb_current_max = json.getInt("usb_current_max");
        data.usb_type = json.getString("usb_type");
        data.wireless_online = json.getInt("wireless_online");
        data.wireless_voltage_now = json.getInt("wireless_voltage_now");
        data.wireless_voltage_max = json.getInt("wireless_voltage_max");
        data.wireless_current_max = json.getInt("wireless_current_max");
        data.wireless_type = json.getString("wireless_type");
        data.scenario_fcc = json.getInt("scenario_fcc");
        data.nt_abnormal_status = json.getInt("nt_abnormal_status");
        
        return data;
    }
    
    // 电池相关getter方法
    public int getCapacity() {
        return capacity;
    }

    public int getVoltageNow() {
        return voltage_now/1000;
    }

        // data.voltage_max = json.getInt("voltage_max");
        // data.voltage_ocv = json.getInt("voltage_ocv");

    public int getCurrentNow() {
        return (int) (current_now/1000.0f);
    }
    
    public int getCurrentAverage() {
        return (int) (current_avg/1000.0f);
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
        return (int) (charge_counter/1000.0f);
    }

    public int getCycleCount() {
        return cycle_count;
    }

    public int getChargeFull() {
        return (int) (charge_full/1000.0f);
    }

    public int getChargeDesign() {
        return (int) (charge_design/1000.0f);
    }

        // data.usb_online = json.getInt("usb_online");
        // data.usb_voltage_now = json.getInt("usb_voltage_now");
        // data.usb_voltage_max = json.getInt("usb_voltage_max");
        // data.in_current_now = json.getInt("in_current_now");
        // data.usb_current_max = json.getInt("usb_current_max");
        // data.usb_type = json.getString("usb_type");
        // data.wireless_online = json.getInt("wireless_online");
        // data.wireless_voltage_now = json.getInt("wireless_voltage_now");
        // data.wireless_voltage_max = json.getInt("wireless_voltage_max");
        // data.wireless_current_max = json.getInt("wireless_current_max");
        // data.wireless_type = json.getString("wireless_type");
        // data.scenario_fcc = json.getInt("scenario_fcc");
        // data.nt_abnormal_status = json.getInt("nt_abnormal_status");
    
    @Override
    public String toString() {
        return "BatteryData{" +
                "timestamp=" + timestamp +
                '}';
    }
}