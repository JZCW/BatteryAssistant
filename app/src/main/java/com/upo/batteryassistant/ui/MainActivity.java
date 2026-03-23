package com.upo.batteryassistant.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
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
    private com.upo.batteryassistant.ui.stats.StatsFragment statsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 在 Activity 创建前根据用户设置应用主题模式
        ThemeHelper.applySavedTheme(this);
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
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_battery_info));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_stats));
        
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
        // 检查并弹出 ChargeHistoryFragment（如果它在栈顶）
        androidx.fragment.app.FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStack();
            fragmentManager.executePendingTransactions();
        }
        
        // 确保Fragment已创建
        if (batteryInfoFragment == null) {
            batteryInfoFragment = new BatteryInfoFragment();
        }
        if (chargeHistoryFragment == null) {
            chargeHistoryFragment = new ChargeHistoryFragment();
        }
        if (statsFragment == null) {
            statsFragment = new com.upo.batteryassistant.ui.stats.StatsFragment();
        }
        
        // 使用show/hide而不是replace，保持Fragment状态
        androidx.fragment.app.FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        
        if (position == 0) {
            // 显示电池信息Fragment
            if (!batteryInfoFragment.isAdded()) {
                transaction.add(R.id.fragment_container, batteryInfoFragment);
            } else {
                transaction.show(batteryInfoFragment);
            }
            // 隐藏其他Fragment
            if (chargeHistoryFragment.isAdded()) {
                transaction.hide(chargeHistoryFragment);
            }
            if (statsFragment.isAdded()) {
                transaction.hide(statsFragment);
            }
        } else if (position == 1) {
            // 显示统计Fragment
            if (!statsFragment.isAdded()) {
                transaction.add(R.id.fragment_container, statsFragment);
            } else {
                transaction.show(statsFragment);
            }
            // 隐藏其他Fragment
            if (batteryInfoFragment.isAdded()) {
                transaction.hide(batteryInfoFragment);
            }
            if (chargeHistoryFragment.isAdded()) {
                transaction.hide(chargeHistoryFragment);
            }
        }
        
        transaction.commit();
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


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_theme) {
            showThemeChooser();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showThemeChooser() {
        final String[] items = new String[]{
            getString(R.string.theme_follow_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark)
        };
        int currentMode = ThemeHelper.getSavedThemeMode(this);
        int checkedItem;
        switch (currentMode) {
            case ThemeHelper.MODE_LIGHT:
                checkedItem = 1;
                break;
            case ThemeHelper.MODE_DARK:
                checkedItem = 2;
                break;
            case ThemeHelper.MODE_FOLLOW_SYSTEM:
            default:
                checkedItem = 0;
                break;
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.action_theme)
            .setSingleChoiceItems(items, checkedItem, (dialog, which) -> {
                int mode;
                if (which == 1) {
                    mode = ThemeHelper.MODE_LIGHT;
                } else if (which == 2) {
                    mode = ThemeHelper.MODE_DARK;
                } else {
                    mode = ThemeHelper.MODE_FOLLOW_SYSTEM;
                }
                ThemeHelper.saveThemeMode(this, mode);
                dialog.dismiss();
                // 重新创建 Activity 以应用新的主题
                recreate();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /**
     * 获取电池信息管理器
     */
    public BatteryInfoManager getBatteryInfoManager() {
        return batteryInfoManager;
    }
}

