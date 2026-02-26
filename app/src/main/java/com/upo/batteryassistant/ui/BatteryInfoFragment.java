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
    private TextView tvHealthApi;
    // private TextView tvPlugged;
    private TextView tvCurrentAverage;
    private TextView tvChargeTime;
    private TextView tvChargeCounter;
    private TextView tvCycleCount;
    private TextView tvFullCapacity;
    private TextView tvDesignCapacity;

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
                if (info.getLevel() != -1) {
                    tvLevel.setText(String.format("%d%%", info.getLevel()));
                } else {
                    tvLevel.setText("不可用");
                }

                // 充电状态
                tvStatus.setText(info.getStatusText());

                // 电压
                if (info.getVoltage() != -1) {
                    tvVoltage.setText(String.format("%.2f V", info.getVoltageVolts()));
                } else {
                    tvVoltage.setText("不可用");
                }

                // 温度
                if (info.getTemperature() != -1) {
                    tvTemperature.setText(String.format("%.1f °C", info.getTemperatureCelsius()));
                } else {
                    tvTemperature.setText("不可用");
                }

                // 电流（微安转毫安）
                if (info.getCurrent() != Integer.MIN_VALUE) {
                    tvCurrent.setText(String.format("%d mA", info.getCurrent()));
                } else {
                    tvCurrent.setText("不可用");
                }

                // 健康度
                if (info.getHealth() != -1) {
                    tvHealth.setText(String.format("%d", info.getHealth()));
                } else {
                    tvHealth.setText("不可用");
                }

                // 健康状态
                tvHealthApi.setText(info.getHealthText());

                // // 插电方式
                // tvPlugged.setText(info.getPluggedText());

                // 平均电流
                if (info.getCurrentAverage() != Integer.MIN_VALUE) {
                    tvCurrentAverage.setText(String.format("%d mA", info.getCurrentAverage()));
                } else {
                    tvCurrentAverage.setText("不可用");
                }

                // 剩余充电时间
                tvChargeTime.setText(info.getChargeTimeRemainingText());

                // 充电计数器
                if (info.getChargeCounter() != -1) {
                    tvChargeCounter.setText(String.format("%d mAh", info.getChargeCounter()));
                } else {
                    tvChargeCounter.setText("不可用");
                }

                // 循环次数
                if (info.getCycleCount() != -1) {
                    tvCycleCount.setText(String.format("%d 次", info.getCycleCount()));
                } else {
                    tvCycleCount.setText("不可用");
                }

                // 满电容量
                if (info.getFullCapacity() != -1) {
                    tvFullCapacity.setText(String.format("%d mAh", info.getFullCapacity()));
                } else {
                    tvFullCapacity.setText("不可用");
                }

                // 设计容量
                if (info.getDesignCapacity() != -1) {
                    tvDesignCapacity.setText(String.format("%d mAh", info.getDesignCapacity()));
                } else {
                    tvDesignCapacity.setText("不可用");
                }
            }
        });
    }
}

