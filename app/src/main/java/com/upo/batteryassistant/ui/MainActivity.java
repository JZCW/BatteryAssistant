package com.upo.batteryassistant.ui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.upo.batteryassistant.R;
import com.upo.batteryassistant.manager.BatteryInfoManager;

/**
 * 主界面Activity
 */
public class MainActivity extends AppCompatActivity {
    private BatteryInfoManager batteryInfoManager;
    private BatteryInfoFragment batteryInfoFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化电池信息管理器
        batteryInfoManager = BatteryInfoManager.getInstance(this);

        // 设置Fragment
        if (savedInstanceState == null) {
            batteryInfoFragment = new BatteryInfoFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, batteryInfoFragment)
                    .commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 注册电池状态监听
        batteryInfoManager.registerBatteryReceiver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 注销电池状态监听
        batteryInfoManager.unregisterBatteryReceiver();
    }

    /**
     * 获取电池信息管理器
     */
    public BatteryInfoManager getBatteryInfoManager() {
        return batteryInfoManager;
    }
}

