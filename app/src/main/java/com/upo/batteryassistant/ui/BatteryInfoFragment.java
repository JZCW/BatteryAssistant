package com.upo.batteryassistant.ui;

import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.upo.batteryassistant.R;
import com.upo.batteryassistant.data.BatteryInfo;
import com.upo.batteryassistant.manager.BatteryInfoManager;

/**
 * 电池信息Fragment
 */
public class BatteryInfoFragment extends Fragment {
    private BatteryInfoManager batteryInfoManager;

    // UI组件 - 基础信息
    private TextView tvLevel;
    private TextView tvStatus;
    private TextView tvVoltage;
    private TextView tvTemperature;
    private TextView tvCurrent;
    private TextView tvHealth;
    private TextView tvPlugged;
    private TextView tvLow;
    private TextView tvCurrentAverage;
    private TextView tvChargeTime;
    private TextView tvChargeCounter;
    private TextView tvCycleCount;

    // UI组件 - 高级信息
    private TextView tvFullCapacity;
  private TextView tvAdvBattTemp;
  private TextView tvAdvBattVoltageNow;
  private TextView tvAdvBattVoltageMax;
  private TextView tvAdvBattVoltageOcv;
  private TextView tvAdvBattCurrentNow;
  private TextView tvAdvBattCurrentAvg;
  private TextView tvAdvBattPowerNow;
  private TextView tvAdvBattPowerAvg;
  private TextView tvAdvBattChargeCounter;
  private TextView tvAdvBattChargeFull;
  private TextView tvAdvBattChargeFullDesign;
  private TextView tvAdvBattCycleCountAdv;
  private TextView tvAdvBattTtfNow;
  private TextView tvAdvBattTtfAvg;
  private TextView tvAdvBattTteAvg;
  private TextView tvAdvBattCcStart;
  private TextView tvAdvBattCcEnd;
  private TextView tvAdvBattCcLimit;
  private TextView tvAdvBattCcLimitMax;

  private TextView tvAdvUsbOnline;
  private TextView tvAdvUsbVoltageNow;
  private TextView tvAdvUsbVoltageMax;
  private TextView tvAdvUsbCurrentNow;
  private TextView tvAdvUsbCurrentMax;
  private TextView tvAdvUsbIcl;
  private TextView tvAdvUsbTemp;
  private TextView tvAdvUsbType;

  private TextView tvAdvWlsOnline;
  private TextView tvAdvWlsVoltageNow;
  private TextView tvAdvWlsVoltageMax;
  private TextView tvAdvWlsCurrentNow;
  private TextView tvAdvWlsCurrentMax;
  private TextView tvAdvWlsIcl;
  private TextView tvAdvWlsTemp;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActivity() != null) {
            batteryInfoManager = BatteryInfoManager.getInstance(getActivity());
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_battery_info, container, false);
        initViews(view);
        
        
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // 设置监听器
        batteryInfoManager.setListener(new BatteryInfoManager.BatteryInfoListener() {
            @Override
            public void onBatteryInfoChanged(BatteryInfo batteryInfo) {
                updateBatteryInfo(batteryInfo);
            }
        });

        // 立即获取一次电池信息
        BatteryInfo info = batteryInfoManager.getCurrentBatteryInfo();
        if (info != null) {
            updateBatteryInfo(info);
        }
    }

    private void initViews(View view) {
        // 基础信息
        tvLevel = view.findViewById(R.id.tv_level);
        tvStatus = view.findViewById(R.id.tv_status);
        tvVoltage = view.findViewById(R.id.tv_voltage);
        tvTemperature = view.findViewById(R.id.tv_temperature);
        tvCurrent = view.findViewById(R.id.tv_current);
        tvHealth = view.findViewById(R.id.tv_health);
        tvPlugged = view.findViewById(R.id.tv_plugged);
        tvLow = view.findViewById(R.id.tv_low);
        tvCurrentAverage = view.findViewById(R.id.tv_current_average);
        tvChargeTime = view.findViewById(R.id.tv_charge_time);
        tvChargeCounter = view.findViewById(R.id.tv_charge_counter);
        tvCycleCount = view.findViewById(R.id.tv_cycle_count);

        // 高级信息
        tvFullCapacity = view.findViewById(R.id.tv_full_capacity);
    tvAdvBattTemp = view.findViewById(R.id.tv_adv_batt_temp);
    tvAdvBattVoltageNow = view.findViewById(R.id.tv_adv_batt_voltage_now);
    tvAdvBattVoltageMax = view.findViewById(R.id.tv_adv_batt_voltage_max);
    tvAdvBattVoltageOcv = view.findViewById(R.id.tv_adv_batt_voltage_ocv);
    tvAdvBattCurrentNow = view.findViewById(R.id.tv_adv_batt_current_now);
    tvAdvBattCurrentAvg = view.findViewById(R.id.tv_adv_batt_current_avg);
    tvAdvBattPowerNow = view.findViewById(R.id.tv_adv_batt_power_now);
    tvAdvBattPowerAvg = view.findViewById(R.id.tv_adv_batt_power_avg);
    tvAdvBattChargeCounter = view.findViewById(R.id.tv_adv_batt_charge_counter);
    tvAdvBattChargeFull = view.findViewById(R.id.tv_adv_batt_charge_full);
    tvAdvBattChargeFullDesign = view.findViewById(R.id.tv_adv_batt_charge_full_design);
    tvAdvBattCycleCountAdv = view.findViewById(R.id.tv_adv_batt_cycle_count);
    tvAdvBattTtfNow = view.findViewById(R.id.tv_adv_batt_ttf_now);
    tvAdvBattTtfAvg = view.findViewById(R.id.tv_adv_batt_ttf_avg);
    tvAdvBattTteAvg = view.findViewById(R.id.tv_adv_batt_tte_avg);
    tvAdvBattCcStart = view.findViewById(R.id.tv_adv_batt_cc_start);
    tvAdvBattCcEnd = view.findViewById(R.id.tv_adv_batt_cc_end);
    tvAdvBattCcLimit = view.findViewById(R.id.tv_adv_batt_cc_limit);
    tvAdvBattCcLimitMax = view.findViewById(R.id.tv_adv_batt_cc_limit_max);

    tvAdvUsbOnline = view.findViewById(R.id.tv_adv_usb_online);
    tvAdvUsbVoltageNow = view.findViewById(R.id.tv_adv_usb_voltage_now);
    tvAdvUsbVoltageMax = view.findViewById(R.id.tv_adv_usb_voltage_max);
    tvAdvUsbCurrentNow = view.findViewById(R.id.tv_adv_usb_current_now);
    tvAdvUsbCurrentMax = view.findViewById(R.id.tv_adv_usb_current_max);
    tvAdvUsbIcl = view.findViewById(R.id.tv_adv_usb_icl);
    tvAdvUsbTemp = view.findViewById(R.id.tv_adv_usb_temp);
    tvAdvUsbType = view.findViewById(R.id.tv_adv_usb_type);

    tvAdvWlsOnline = view.findViewById(R.id.tv_adv_wls_online);
    tvAdvWlsVoltageNow = view.findViewById(R.id.tv_adv_wls_voltage_now);
    tvAdvWlsVoltageMax = view.findViewById(R.id.tv_adv_wls_voltage_max);
    tvAdvWlsCurrentNow = view.findViewById(R.id.tv_adv_wls_current_now);
    tvAdvWlsCurrentMax = view.findViewById(R.id.tv_adv_wls_current_max);
    tvAdvWlsIcl = view.findViewById(R.id.tv_adv_wls_icl);
    tvAdvWlsTemp = view.findViewById(R.id.tv_adv_wls_temp);
    }

    private void updateBatteryInfo(BatteryInfo info) {
        if (getActivity() == null || info == null) {
            return;
        }

        // 在主线程更新UI
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // 电量百分比
                tvLevel.setText(String.format("%d%%", info.getLevel()));

                // 充电状态
                tvStatus.setText(info.getStatusText());

                // 电压
                tvVoltage.setText(String.format("%.2f V", info.getVoltageVolts()));

                // 温度
                tvTemperature.setText(String.format("%.1f °C", info.getTemperatureCelsius()));

                // 电流（微安转毫安）
                if (info.getCurrent() != 0 && info.getCurrent() != Integer.MIN_VALUE) {
                    tvCurrent.setText(String.format("%.1f mA", info.getCurrentMilliAmps()));
                } else {
                    tvCurrent.setText("不可用");
                }

                // 健康状态
                tvHealth.setText(info.getHealthText());

                // 插电方式
                tvPlugged.setText(info.getPluggedText());

                // 低电量警告
                tvLow.setText(info.isLow() ? "是" : "否");

                // 平均电流
                if (info.getCurrentAverage() != 0 && info.getCurrentAverage() != Integer.MIN_VALUE) {
                    tvCurrentAverage.setText(String.format("%.1f mA", info.getCurrentAverageMilliAmps()));
                } else {
                    tvCurrentAverage.setText("不可用");
                }

                // 剩余充电时间
                if (info.getChargeTimeRemaining() >= 0) {
                    tvChargeTime.setText(info.getChargeTimeRemainingText());
                } else {
                    tvChargeTime.setText("无法计算");
                }

                // 充电计数器
                if (info.getChargeCounter() != 0 && info.getChargeCounter() != Long.MIN_VALUE) {
                    tvChargeCounter.setText(String.format("%.1f mAh", info.getChargeCounterMilliAmpHours()));
                } else {
                    tvChargeCounter.setText("不可用");
                }

                // 循环次数
                if (info.getCycleCount() >= 0) {
                    tvCycleCount.setText(String.format("%d 次", info.getCycleCount()));
                } else {
                    tvCycleCount.setText("不可用");
                }

                // ========== 高级信息 ==========
                // 满电容量（来自 root 或其他途径）
                int fullCap = info.getFullCapacity();
                if (fullCap > 0) {
                    tvFullCapacity.setText(String.format("%d mAh", fullCap));
                } else {
                    tvFullCapacity.setText("--");
                }

                // 电池高级字段
                if (info.getAdvBattTempDeciC() != 0) {
                    tvAdvBattTemp.setText(String.format("%.1f °C", info.getAdvBattTempDeciC() / 10.0f));
                } else {
                    tvAdvBattTemp.setText("不可用");
                }

                if (info.getAdvBattVoltageNowUv() != 0) {
                    tvAdvBattVoltageNow.setText(String.format("%.3f V", info.getAdvBattVoltageNowUv() / 1_000_000.0f));
                } else {
                    tvAdvBattVoltageNow.setText("不可用");
                }

                if (info.getAdvBattVoltageMaxUv() != 0) {
                    tvAdvBattVoltageMax.setText(String.format("%.3f V", info.getAdvBattVoltageMaxUv() / 1_000_000.0f));
                } else {
                    tvAdvBattVoltageMax.setText("不可用");
                }

                if (info.getAdvBattVoltageOcvUv() != 0) {
                    tvAdvBattVoltageOcv.setText(String.format("%.3f V", info.getAdvBattVoltageOcvUv() / 1_000_000.0f));
                } else {
                    tvAdvBattVoltageOcv.setText("不可用");
                }

                if (info.getAdvBattCurrentNowUa() != 0) {
                    tvAdvBattCurrentNow.setText(String.format("%.1f mA", info.getAdvBattCurrentNowUa() / 1000.0f));
                } else {
                    tvAdvBattCurrentNow.setText("不可用");
                }

                if (info.getAdvBattCurrentAvgUa() != 0) {
                    tvAdvBattCurrentAvg.setText(String.format("%.1f mA", info.getAdvBattCurrentAvgUa() / 1000.0f));
                } else {
                    tvAdvBattCurrentAvg.setText("不可用");
                }

                if (info.getAdvBattPowerNowUw() != 0) {
                    tvAdvBattPowerNow.setText(String.format("%.3f W", info.getAdvBattPowerNowUw() / 1_000_000.0f));
                } else {
                    tvAdvBattPowerNow.setText("不可用");
                }

                if (info.getAdvBattPowerAvgUw() != 0) {
                    tvAdvBattPowerAvg.setText(String.format("%.3f W", info.getAdvBattPowerAvgUw() / 1_000_000.0f));
                } else {
                    tvAdvBattPowerAvg.setText("不可用");
                }

                if (info.getAdvBattChargeCounterUah() != 0) {
                    tvAdvBattChargeCounter.setText(String.format("%.1f mAh", info.getAdvBattChargeCounterUah() / 1000.0f));
                } else {
                    tvAdvBattChargeCounter.setText("不可用");
                }

                if (info.getAdvBattChargeFullUah() != 0) {
                    tvAdvBattChargeFull.setText(String.format("%.1f mAh", info.getAdvBattChargeFullUah() / 1000.0f));
                } else {
                    tvAdvBattChargeFull.setText("不可用");
                }

                if (info.getAdvBattChargeFullDesignUah() != 0) {
                    tvAdvBattChargeFullDesign.setText(String.format("%.1f mAh", info.getAdvBattChargeFullDesignUah() / 1000.0f));
                } else {
                    tvAdvBattChargeFullDesign.setText("不可用");
                }

                if (info.getAdvBattCycleCount() > 0) {
                    tvAdvBattCycleCountAdv.setText(String.format("%d 次", info.getAdvBattCycleCount()));
                } else {
                    tvAdvBattCycleCountAdv.setText("不可用");
                }

                if (info.getAdvBattTimeToFullNowSec() > 0) {
                    tvAdvBattTtfNow.setText(info.getAdvBattTimeToFullNowSec() + " s");
                } else {
                    tvAdvBattTtfNow.setText("不可用");
                }

                if (info.getAdvBattTimeToFullAvgSec() > 0) {
                    tvAdvBattTtfAvg.setText(info.getAdvBattTimeToFullAvgSec() + " s");
                } else {
                    tvAdvBattTtfAvg.setText("不可用");
                }

                if (info.getAdvBattTimeToEmptyAvgSec() > 0) {
                    tvAdvBattTteAvg.setText(info.getAdvBattTimeToEmptyAvgSec() + " s");
                } else {
                    tvAdvBattTteAvg.setText("不可用");
                }

                if (info.getAdvBattChargeCtrlStartThr() > 0) {
                    tvAdvBattCcStart.setText(info.getAdvBattChargeCtrlStartThr() + " %");
                } else {
                    tvAdvBattCcStart.setText("不可用");
                }

                if (info.getAdvBattChargeCtrlEndThr() > 0) {
                    tvAdvBattCcEnd.setText(info.getAdvBattChargeCtrlEndThr() + " %");
                } else {
                    tvAdvBattCcEnd.setText("不可用");
                }

                if (info.getAdvBattChargeCtrlLimit() > 0) {
                    tvAdvBattCcLimit.setText(info.getAdvBattChargeCtrlLimit() + " (原始)");
                } else {
                    tvAdvBattCcLimit.setText("不可用");
                }

                if (info.getAdvBattChargeCtrlLimitMax() > 0) {
                    tvAdvBattCcLimitMax.setText(info.getAdvBattChargeCtrlLimitMax() + " (原始)");
                } else {
                    tvAdvBattCcLimitMax.setText("不可用");
                }

                // USB 高级字段
                tvAdvUsbOnline.setText(info.isAdvUsbOnline() ? "是" : "否");

                if (info.getAdvUsbVoltageNowUv() != 0) {
                    tvAdvUsbVoltageNow.setText(String.format("%.3f V", info.getAdvUsbVoltageNowUv() / 1_000_000.0f));
                } else {
                    tvAdvUsbVoltageNow.setText("不可用");
                }

                if (info.getAdvUsbVoltageMaxUv() != 0) {
                    tvAdvUsbVoltageMax.setText(String.format("%.3f V", info.getAdvUsbVoltageMaxUv() / 1_000_000.0f));
                } else {
                    tvAdvUsbVoltageMax.setText("不可用");
                }

                if (info.getAdvUsbCurrentNowUa() != 0) {
                    tvAdvUsbCurrentNow.setText(String.format("%.1f mA", info.getAdvUsbCurrentNowUa() / 1000.0f));
                } else {
                    tvAdvUsbCurrentNow.setText("不可用");
                }

                if (info.getAdvUsbCurrentMaxUa() != 0) {
                    tvAdvUsbCurrentMax.setText(String.format("%.1f mA", info.getAdvUsbCurrentMaxUa() / 1000.0f));
                } else {
                    tvAdvUsbCurrentMax.setText("不可用");
                }

                if (info.getAdvUsbInputCurrentLimitUa() != 0) {
                    tvAdvUsbIcl.setText(String.format("%.1f mA", info.getAdvUsbInputCurrentLimitUa() / 1000.0f));
                } else {
                    tvAdvUsbIcl.setText("不可用");
                }

                if (info.getAdvUsbTempDeciC() != 0) {
                    tvAdvUsbTemp.setText(String.format("%.1f °C", info.getAdvUsbTempDeciC() / 10.0f));
                } else {
                    tvAdvUsbTemp.setText("不可用");
                }

                if (info.getAdvUsbType() != null && !info.getAdvUsbType().isEmpty()) {
                    tvAdvUsbType.setText(info.getAdvUsbType());
                } else {
                    tvAdvUsbType.setText("不可用");
                }

                // 无线高级字段
                tvAdvWlsOnline.setText(info.isAdvWlsOnline() ? "是" : "否");

                if (info.getAdvWlsVoltageNowUv() != 0) {
                    tvAdvWlsVoltageNow.setText(String.format("%.3f V", info.getAdvWlsVoltageNowUv() / 1_000_000.0f));
                } else {
                    tvAdvWlsVoltageNow.setText("不可用");
                }

                if (info.getAdvWlsVoltageMaxUv() != 0) {
                    tvAdvWlsVoltageMax.setText(String.format("%.3f V", info.getAdvWlsVoltageMaxUv() / 1_000_000.0f));
                } else {
                    tvAdvWlsVoltageMax.setText("不可用");
                }

                if (info.getAdvWlsCurrentNowUa() != 0) {
                    tvAdvWlsCurrentNow.setText(String.format("%.1f mA", info.getAdvWlsCurrentNowUa() / 1000.0f));
                } else {
                    tvAdvWlsCurrentNow.setText("不可用");
                }

                if (info.getAdvWlsCurrentMaxUa() != 0) {
                    tvAdvWlsCurrentMax.setText(String.format("%.1f mA", info.getAdvWlsCurrentMaxUa() / 1000.0f));
                } else {
                    tvAdvWlsCurrentMax.setText("不可用");
                }

                if (info.getAdvWlsInputCurrentLimitUa() != 0) {
                    tvAdvWlsIcl.setText(String.format("%.1f mA", info.getAdvWlsInputCurrentLimitUa() / 1000.0f));
                } else {
                    tvAdvWlsIcl.setText("不可用");
                }

                if (info.getAdvWlsTempDeciC() != 0) {
                    tvAdvWlsTemp.setText(String.format("%.1f °C", info.getAdvWlsTempDeciC() / 10.0f));
                } else {
                    tvAdvWlsTemp.setText("不可用");
                }
            }
        });
    }
}

