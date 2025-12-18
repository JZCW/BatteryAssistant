package com.upo.batteryassistant.util;

import com.upo.batteryassistant.data.BatteryInfo;

/**
 * 通过 root 从 /sys/class/power_supply 读取电池/供电的高级信息，
 * 并填充到 BatteryInfo 的 adv* 字段中。
 */
public class BatterySysfsReader {

  private static final String BASE_BATT = "/sys/class/power_supply/battery/";
  private static final String BASE_USB = "/sys/class/power_supply/usb/";
  private static final String BASE_WLS = "/sys/class/power_supply/wireless/";

  private BatterySysfsReader() {
  }

  private static Integer readInt(String path) {
    if (!RootUtil.fileExists(path)) {
      return null;
    }
    String s = RootUtil.readFile(path);
    if (s == null) {
      return null;
    }
    try {
      return Integer.parseInt(s.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Long readLong(String path) {
    if (!RootUtil.fileExists(path)) {
      return null;
    }
    String s = RootUtil.readFile(path);
    if (s == null) {
      return null;
    }
    try {
      return Long.parseLong(s.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String readString(String path) {
    if (!RootUtil.fileExists(path)) {
      return null;
    }
    String s = RootUtil.readFile(path);
    if (s == null) {
      return null;
    }
    return s.trim();
  }

  // ========== battery ==========

  public static void fillBatteryAdvancedFields(BatteryInfo info) {
    if (info == null) {
      return;
    }

    // capacity（可能已有 level，这里只是镜像）
    Integer cap = readInt(BASE_BATT + "capacity");
    if (cap != null) {
      info.setAdvBattCapacity(cap);
    }

    // 温度 / 电压 / 电流等
    Integer temp = readInt(BASE_BATT + "temp");
    if (temp != null) {
      info.setAdvBattTempDeciC(temp);
    }

    Long voltNow = readLong(BASE_BATT + "voltage_now");
    if (voltNow != null) {
      info.setAdvBattVoltageNowUv(voltNow);
    }

    Long voltMax = readLong(BASE_BATT + "voltage_max");
    if (voltMax != null) {
      info.setAdvBattVoltageMaxUv(voltMax);
    }

    Long voltOcv = readLong(BASE_BATT + "voltage_ocv");
    if (voltOcv != null) {
      info.setAdvBattVoltageOcvUv(voltOcv);
    }

    Integer currNow = readInt(BASE_BATT + "current_now");
    if (currNow != null) {
      info.setAdvBattCurrentNowUa(currNow);
    }

    Integer currAvg = readInt(BASE_BATT + "current_avg");
    if (currAvg != null) {
      info.setAdvBattCurrentAvgUa(currAvg);
    }

    Long pwrNow = readLong(BASE_BATT + "power_now");
    if (pwrNow != null) {
      info.setAdvBattPowerNowUw(pwrNow);
    }

    Long pwrAvg = readLong(BASE_BATT + "power_avg");
    if (pwrAvg != null) {
      info.setAdvBattPowerAvgUw(pwrAvg);
    }

    // 容量/寿命
    Long chargeCounter = readLong(BASE_BATT + "charge_counter");
    if (chargeCounter != null) {
      info.setAdvBattChargeCounterUah(chargeCounter);
      // 保持与原有字段一定同步：微安时 -> 毫安时
      // info.setChargeCounter(chargeCounter);
    }

    Long chargeFull = readLong(BASE_BATT + "charge_full");
    if (chargeFull != null) {
      info.setAdvBattChargeFullUah(chargeFull);
      // 也同步一份到 fullCapacity（mAh）
      // info.setFullCapacity((int) (chargeFull / 1000));
    }

    Long chargeFullDesign = readLong(BASE_BATT + "charge_full_design");
    if (chargeFullDesign != null) {
      info.setAdvBattChargeFullDesignUah(chargeFullDesign);
    }

    Integer cycle = readInt(BASE_BATT + "cycle_count");
    if (cycle != null) {
      info.setAdvBattCycleCount(cycle);
      // info.setCycleCount(cycle);
    }

    Integer tte = readInt(BASE_BATT + "time_to_empty_avg");
    if (tte != null) {
      info.setAdvBattTimeToEmptyAvgSec(tte);
    }

    Integer ttfAvg = readInt(BASE_BATT + "time_to_full_avg");
    if (ttfAvg != null) {
      info.setAdvBattTimeToFullAvgSec(ttfAvg);
    }

    Integer ttfNow = readInt(BASE_BATT + "time_to_full_now");
    if (ttfNow != null) {
      info.setAdvBattTimeToFullNowSec(ttfNow);
    }

    // 充电控制
    Integer startThr = readInt(BASE_BATT + "charge_control_start_threshold");
    if (startThr != null) {
      info.setAdvBattChargeCtrlStartThr(startThr);
    }

    Integer endThr = readInt(BASE_BATT + "charge_control_end_threshold");
    if (endThr != null) {
      info.setAdvBattChargeCtrlEndThr(endThr);
    }

    Integer limit = readInt(BASE_BATT + "charge_control_limit");
    if (limit != null) {
      info.setAdvBattChargeCtrlLimit(limit);
    }

    Integer limitMax = readInt(BASE_BATT + "charge_control_limit_max");
    if (limitMax != null) {
      info.setAdvBattChargeCtrlLimitMax(limitMax);
    }

    // 文本信息：technology / model_name / status / health
    String tech = readString(BASE_BATT + "technology");
    if (tech != null) {
      info.setAdvBattTechnology(tech);
    }

    String model = readString(BASE_BATT + "model_name");
    if (model != null) {
      info.setAdvBattModelName(model);
    }

    String status = readString(BASE_BATT + "status");
    if (status != null) {
      info.setAdvBattStatusText(status);
    }

    String health = readString(BASE_BATT + "health");
    if (health != null) {
      info.setAdvBattHealthText(health);
    }

    String chargeType = readString(BASE_BATT + "charge_type");
    if (chargeType != null) {
      info.setAdvBattChargeType(chargeType);
    }

    Integer present = readInt(BASE_BATT + "present");
    if (present != null) {
      info.setAdvBattPresent(present);
    }
  }

  // ========== usb ==========

  public static void fillUsbAdvancedFields(BatteryInfo info) {
    if (info == null) {
      return;
    }
    if (!RootUtil.fileExists(BASE_USB)) {
      return;
    }

    Integer online = readInt(BASE_USB + "online");
    if (online != null) {
      info.setAdvUsbOnline(online != 0);
    }

    Long voltNow = readLong(BASE_USB + "voltage_now");
    if (voltNow != null) {
      info.setAdvUsbVoltageNowUv(voltNow);
    }

    Long voltMax = readLong(BASE_USB + "voltage_max");
    if (voltMax != null) {
      info.setAdvUsbVoltageMaxUv(voltMax);
    }

    Integer currNow = readInt(BASE_USB + "current_now");
    if (currNow != null) {
      info.setAdvUsbCurrentNowUa(currNow);
    }

    Integer currMax = readInt(BASE_USB + "current_max");
    if (currMax != null) {
      info.setAdvUsbCurrentMaxUa(currMax);
    }

    Integer icl = readInt(BASE_USB + "input_current_limit");
    if (icl != null) {
      info.setAdvUsbInputCurrentLimitUa(icl);
    }

    Integer temp = readInt(BASE_USB + "temp");
    if (temp != null) {
      info.setAdvUsbTempDeciC(temp);
    }

    String usbType = readString(BASE_USB + "usb_type");
    if (usbType != null) {
      info.setAdvUsbType(usbType);
    }
  }

  // ========== wireless ==========

  public static void fillWirelessAdvancedFields(BatteryInfo info) {
    if (info == null) {
      return;
    }
    if (!RootUtil.fileExists(BASE_WLS)) {
      return;
    }

    Integer online = readInt(BASE_WLS + "online");
    if (online != null) {
      info.setAdvWlsOnline(online != 0);
    }

    Long voltNow = readLong(BASE_WLS + "voltage_now");
    if (voltNow != null) {
      info.setAdvWlsVoltageNowUv(voltNow);
    }

    Long voltMax = readLong(BASE_WLS + "voltage_max");
    if (voltMax != null) {
      info.setAdvWlsVoltageMaxUv(voltMax);
    }

    Integer currNow = readInt(BASE_WLS + "current_now");
    if (currNow != null) {
      info.setAdvWlsCurrentNowUa(currNow);
    }

    Integer currMax = readInt(BASE_WLS + "current_max");
    if (currMax != null) {
      info.setAdvWlsCurrentMaxUa(currMax);
    }

    Integer icl = readInt(BASE_WLS + "input_current_limit");
    if (icl != null) {
      info.setAdvWlsInputCurrentLimitUa(icl);
    }

    Integer temp = readInt(BASE_WLS + "temp");
    if (temp != null) {
      info.setAdvWlsTempDeciC(temp);
    }
  }
}


