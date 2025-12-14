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
                // 满电容量
                tvFullCapacity.setText(String.format("%d mAh", info.getFullCapacity()));
            }
        });
    }
}

