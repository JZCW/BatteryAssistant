package com.upo.batteryassistant.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.upo.batteryassistant.R;
import com.upo.batteryassistant.data.BatteryInfo;
import com.upo.batteryassistant.manager.BatteryInfoManager;

/**
 * 电池信息Fragment
 */
public class BatteryInfoFragment extends Fragment {
    private BatteryInfoManager batteryInfoManager;
    private Handler handler;
    private Runnable updateRunnable;
    private static final int UPDATE_INTERVAL = 2000;

    // UI组件 - 基础信息
    private TextView tvLevel;
    private TextView tvStatus;
    private TextView tvVoltage;
    private TextView tvTemperature;
    private TextView tvCurrent;
    private TextView tvHealth;
    private TextView tvHealthApi;
    // private TextView tvPlugged;
    private TextView tvCurrentAverage;
    private TextView tvChargeTime;
    private TextView tvChargeCounter;
    private TextView tvCycleCount;
    private TextView tvFullCapacity;
    private TextView tvDesignCapacity;
    private TextView tvUsbOnline;
    private TextView tvUsbVoltageNow;
    private TextView tvUsbVoltageMax;
    private TextView tvUsbCurrentMax;
    private TextView tvUsbType;
    private TextView tvWirelessOnline;
    private TextView tvWirelessVoltageNow;
    private TextView tvWirelessVoltageMax;
    private TextView tvWirelessCurrentMax;
    private TextView tvWirelessType;
    private TextView tvInCurrentNow;
    private TextView tvScenarioFcc;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getActivity() != null) {
            batteryInfoManager = BatteryInfoManager.getInstance(getActivity());
            handler = new Handler(Looper.getMainLooper());
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
        
        // 创建定时任务
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateBatteryInfo();
                // 循环执行
                if (handler != null) {
                    handler.postDelayed(this, UPDATE_INTERVAL);
                }
            }
        };
    }

    private void initViews(View view) {
        tvLevel = view.findViewById(R.id.tv_level);
        tvStatus = view.findViewById(R.id.tv_status);
        tvVoltage = view.findViewById(R.id.tv_voltage);
        tvTemperature = view.findViewById(R.id.tv_temperature);
        tvCurrent = view.findViewById(R.id.tv_current);
        tvHealth = view.findViewById(R.id.tv_health);
        tvHealthApi = view.findViewById(R.id.tv_health_api);
        // tvPlugged = view.findViewById(R.id.tv_plugged);
        tvCurrentAverage = view.findViewById(R.id.tv_current_average);
        tvChargeTime = view.findViewById(R.id.tv_charge_time);
        tvChargeCounter = view.findViewById(R.id.tv_charge_counter);
        tvCycleCount = view.findViewById(R.id.tv_cycle_count);
        tvFullCapacity = view.findViewById(R.id.tv_full_capacity);
        tvDesignCapacity = view.findViewById(R.id.tv_design_capacity);
        tvUsbOnline = view.findViewById(R.id.tv_usb_online);
        tvUsbVoltageNow = view.findViewById(R.id.tv_usb_voltage_now);
        tvUsbVoltageMax = view.findViewById(R.id.tv_usb_voltage_max);
        tvUsbCurrentMax = view.findViewById(R.id.tv_usb_current_max);
        tvUsbType = view.findViewById(R.id.tv_usb_type);
        tvWirelessOnline = view.findViewById(R.id.tv_wireless_online);
        tvWirelessVoltageNow = view.findViewById(R.id.tv_wireless_voltage_now);
        tvWirelessVoltageMax = view.findViewById(R.id.tv_wireless_voltage_max);
        tvWirelessCurrentMax = view.findViewById(R.id.tv_wireless_current_max);
        tvWirelessType = view.findViewById(R.id.tv_wireless_type);
        tvInCurrentNow = view.findViewById(R.id.tv_in_current_now);
        tvScenarioFcc = view.findViewById(R.id.tv_scenario_fcc);
    }

    private void updateBatteryInfo() {
        // 获取电池信息
        BatteryInfo info = batteryInfoManager.getCurrentBatteryInfo();
        if (getActivity() == null || info == null) {
            return;
        }

        // 在主线程更新UI
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // 电量百分比
                if (info.getLevel() != -1) {
                    tvLevel.setText(getString(R.string.battery_percent_value, info.getLevel()));
                } else {
                    tvLevel.setText(R.string.common_unavailable);
                }

                // 充电状态
                tvStatus.setText(info.getStatusText());

                // 电压
                if (info.getVoltage() != -1) {
                    tvVoltage.setText(getString(R.string.battery_voltage_value, info.getVoltageVolts()));
                } else {
                    tvVoltage.setText(R.string.common_unavailable);
                }

                // 温度
                if (info.getTemperature() != -1) {
                    tvTemperature.setText(getString(R.string.battery_temperature_value, info.getTemperatureCelsius()));
                } else {
                    tvTemperature.setText(R.string.common_unavailable);
                }

                // 电流（微安转毫安）
                if (info.getCurrent() != Integer.MIN_VALUE) {
                    tvCurrent.setText(getString(R.string.battery_current_value, info.getCurrent()));
                } else {
                    tvCurrent.setText(R.string.common_unavailable);
                }

                // 健康度
                if (info.getHealth() != -1) {
                    tvHealth.setText(getString(R.string.battery_int_value, info.getHealth()));
                } else {
                    tvHealth.setText(R.string.common_unavailable);
                }

                // 健康状态
                tvHealthApi.setText(info.getHealthText());

                // // 插电方式
                // tvPlugged.setText(info.getPluggedText());

                // 平均电流
                if (info.getCurrentAverage() != Integer.MIN_VALUE) {
                    tvCurrentAverage.setText(getString(R.string.battery_current_value, info.getCurrentAverage()));
                } else {
                    tvCurrentAverage.setText(R.string.common_unavailable);
                }

                // 剩余充电时间
                tvChargeTime.setText(info.getChargeTimeRemainingText());

                // 充电计数器
                if (info.getChargeCounter() != -1) {
                    tvChargeCounter.setText(getString(R.string.battery_charge_counter_value, info.getChargeCounter()));
                } else {
                    tvChargeCounter.setText(R.string.common_unavailable);
                }

                // 循环次数
                if (info.getCycleCount() != -1) {
                    tvCycleCount.setText(getString(R.string.battery_cycle_count_value, info.getCycleCount()));
                } else {
                    tvCycleCount.setText(R.string.common_unavailable);
                }

                // 满电容量
                if (info.getFullCapacity() != -1) {
                    tvFullCapacity.setText(getString(R.string.battery_charge_counter_value, info.getFullCapacity()));
                } else {
                    tvFullCapacity.setText(R.string.common_unavailable);
                }

                // 设计容量
                if (info.getDesignCapacity() != -1) {
                    tvDesignCapacity.setText(getString(R.string.battery_charge_counter_value, info.getDesignCapacity()));
                } else {
                    tvDesignCapacity.setText(R.string.common_unavailable);
                }

                setOnlineStateText(tvUsbOnline, info.isUsbOnline());
                setVoltageText(tvUsbVoltageNow, info.getUsbVoltageNow(), info.getUsbVoltageNowVolts());
                setVoltageText(tvUsbVoltageMax, info.getUsbVoltageMax(), info.getUsbVoltageMaxVolts());
                setCurrentText(tvUsbCurrentMax, info.getUsbCurrentMax());
                setPlainText(tvUsbType, info.getUsbType());

                setOnlineStateText(tvWirelessOnline, info.isWirelessOnline());
                setVoltageText(tvWirelessVoltageNow, info.getWirelessVoltageNow(), info.getWirelessVoltageNowVolts());
                setVoltageText(tvWirelessVoltageMax, info.getWirelessVoltageMax(), info.getWirelessVoltageMaxVolts());
                setCurrentText(tvWirelessCurrentMax, info.getWirelessCurrentMax());
                setPlainText(tvWirelessType, info.getWirelessType());

                setCurrentText(tvInCurrentNow, info.getInCurrentNow());
                setCurrentText(tvScenarioFcc, info.getScenarioFcc());
            }
        });
    }

    private void setOnlineStateText(TextView textView, boolean isOnline) {
        textView.setText(isOnline ? R.string.battery_info_online : R.string.battery_info_offline);
    }

    private void setVoltageText(TextView textView, int rawValue, float voltageValue) {
        if (rawValue != -1) {
            textView.setText(getString(R.string.battery_voltage_value, voltageValue));
        } else {
            textView.setText(R.string.common_unavailable);
        }
    }

    private void setCurrentText(TextView textView, int value) {
        if (value != Integer.MIN_VALUE && value != -1) {
            textView.setText(getString(R.string.battery_current_value, value));
        } else {
            textView.setText(R.string.common_unavailable);
        }
    }

    private void setPlainText(TextView textView, String value) {
        if (value != null && !value.trim().isEmpty()) {
            textView.setText(value);
        } else {
            textView.setText(R.string.common_unavailable);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // 立即获取一次电池信息
        updateBatteryInfo();
        // Fragment可见时启动定时器
        startPeriodicUpdate();
    }

    @Override
    public void onPause() {
        super.onPause();
        // Fragment不可见时停止定时器
        stopPeriodicUpdate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 清理资源
        stopPeriodicUpdate();
    }

    /**
     * 开始定时更新
     */
    private void startPeriodicUpdate() {
        if (handler != null && updateRunnable != null) {
            handler.post(updateRunnable);
        }
    }

    /**
     * 停止定时更新
     */
    private void stopPeriodicUpdate() {
        if (handler != null && updateRunnable != null) {
            handler.removeCallbacks(updateRunnable);
        }
    }
}

