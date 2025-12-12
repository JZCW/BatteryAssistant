package com.upo.batteryassistant.ui;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.tabs.TabLayout;
import com.upo.batteryassistant.R;
import com.upo.batteryassistant.manager.BatteryInfoManager;
import com.upo.batteryassistant.service.BatteryMonitorService;

/**
 * 主界面Activity
 */
public class MainActivity extends AppCompatActivity {
    private BatteryInfoManager batteryInfoManager;
    private TabLayout tabLayout;
    private BatteryInfoFragment batteryInfoFragment;
    private ChargeHistoryFragment chargeHistoryFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 设置沉浸式布局
        setupImmersiveLayout();

        // 初始化电池信息管理器
        batteryInfoManager = BatteryInfoManager.getInstance(this);

        // 启动电池监控服务
        startBatteryMonitorService();

        // 设置TabLayout
        setupTabs();

        // 设置Fragment
        if (savedInstanceState == null) {
            showFragment(0);
        }
    }
    
    /**
     * 设置TabLayout
     */
    private void setupTabs() {
        tabLayout = findViewById(R.id.tab_layout);
        
        // 添加标签
        tabLayout.addTab(tabLayout.newTab().setText("电池信息"));
        tabLayout.addTab(tabLayout.newTab().setText("充放电历史"));
        
        // 设置标签选择监听
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                showFragment(tab.getPosition());
            }
            
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }
            
            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }
    
    /**
     * 显示指定位置的Fragment
     */
    private void showFragment(int position) {
        Fragment fragment = null;
        
        if (position == 0) {
            if (batteryInfoFragment == null) {
                batteryInfoFragment = new BatteryInfoFragment();
            }
            fragment = batteryInfoFragment;
        } else if (position == 1) {
            if (chargeHistoryFragment == null) {
                chargeHistoryFragment = new ChargeHistoryFragment();
            }
            fragment = chargeHistoryFragment;
        }
        
        if (fragment != null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 注册电池状态监听（用于UI更新）
        batteryInfoManager.registerBatteryReceiver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 注销电池状态监听
        batteryInfoManager.unregisterBatteryReceiver();
    }

    /**
     * 启动电池监控服务
     */
    private void startBatteryMonitorService() {
        Intent serviceIntent = new Intent(this, BatteryMonitorService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    /**
     * 设置沉浸式布局
     */
    private void setupImmersiveLayout() {
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.setSystemBarsAppearance(
                0, // 浅色字
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            );
            // 处理WindowInsets，确保整个Activity内容适应状态栏和导航栏
            controller.setSystemBarsBehavior(
                WindowInsetsController.BEHAVIOR_DEFAULT
            );
        }

        // 设置WindowInsets监听器
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            // 让内容延伸到系统栏下方
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.fragment_container), (view, windowInsets) -> {
                // 获取系统栏的insets
                Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                
                // 设置padding以避免内容被系统栏遮挡
                view.setPadding(
                    systemBars.left,
                    systemBars.top,
                    systemBars.right,
                    0  // 底部不需要padding，因为TabLayout在底部
                );
                
                return windowInsets;
            });
            
            // 为TabLayout设置底部padding，避开导航栏
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.tab_layout), (view, windowInsets) -> {
                Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                view.setPadding(
                    view.getPaddingLeft(),
                    view.getPaddingTop(),
                    view.getPaddingRight(),
                    systemBars.bottom
                );
                return windowInsets;
            });
            
            return insets;
        });
    }

    /**
     * 获取电池信息管理器
     */
    public BatteryInfoManager getBatteryInfoManager() {
        return batteryInfoManager;
    }
}

