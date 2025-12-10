package com.upo.batteryassistant.ui;

import android.os.Bundle;
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

    // UI组件
    private TextView tvLevel;
    private TextView tvStatus;
    private TextView tvVoltage;
    private TextView tvTemperature;
    private TextView tvCurrent;
    private TextView tvHealth;
    private TextView tvTechnology;

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
        tvTechnology = view.findViewById(R.id.tv_technology);
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

                // 电流
                if (info.getCurrent() != 0) {
                    tvCurrent.setText(String.format("%d mA", info.getCurrent()));
                } else {
                    tvCurrent.setText("需要Root权限");
                }

                // 健康状态
                tvHealth.setText(info.getHealthText());

                // 电池技术类型
                if (info.getTechnology() != null && !info.getTechnology().isEmpty()) {
                    tvTechnology.setText(info.getTechnology());
                } else {
                    tvTechnology.setText("未知");
                }
            }
        });
    }
}

