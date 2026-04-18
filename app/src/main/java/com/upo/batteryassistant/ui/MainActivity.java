package com.upo.batteryassistant.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.upo.batteryassistant.R;
import com.upo.batteryassistant.manager.BatteryInfoManager;
import com.upo.batteryassistant.service.BatteryMonitorService;
import com.upo.batteryassistant.ui.util.ThemeHelper;

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
        setTheme(ThemeHelper.getThemeResId(this));
        // 在 Activity 创建前根据用户设置应用主题模式
        ThemeHelper.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupToolbar();
        applyWindowInsets();

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

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.top_toolbar);
        setSupportActionBar(toolbar);
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.main_root);
        View appBarContainer = findViewById(R.id.app_bar_container);
        View fragmentContainer = findViewById(R.id.fragment_container);
        View bottomTabs = findViewById(R.id.tab_layout);

        final int appBarLeft = appBarContainer.getPaddingLeft();
        final int appBarTop = appBarContainer.getPaddingTop();
        final int appBarRight = appBarContainer.getPaddingRight();
        final int appBarBottom = appBarContainer.getPaddingBottom();
        final int fragmentLeft = fragmentContainer.getPaddingLeft();
        final int fragmentTop = fragmentContainer.getPaddingTop();
        final int fragmentRight = fragmentContainer.getPaddingRight();
        final int fragmentBottom = fragmentContainer.getPaddingBottom();
        final int tabsLeft = bottomTabs.getPaddingLeft();
        final int tabsTop = bottomTabs.getPaddingTop();
        final int tabsRight = bottomTabs.getPaddingRight();
        final int tabsBottom = bottomTabs.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            appBarContainer.setPadding(
                appBarLeft + systemBars.left,
                appBarTop + systemBars.top,
                appBarRight + systemBars.right,
                appBarBottom
            );
            fragmentContainer.setPadding(
                fragmentLeft + systemBars.left,
                fragmentTop,
                fragmentRight + systemBars.right,
                fragmentBottom
            );
            bottomTabs.setPadding(
                tabsLeft + systemBars.left,
                tabsTop,
                tabsRight + systemBars.right,
                tabsBottom + systemBars.bottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
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
        // 同步弹出回退栈（popBackStack 是异步的，必须用 popBackStackImmediate 保证执行完毕
        // 再进行 show/hide，否则 replace 恢复的 Fragment 会与新添加的 Fragment 叠加）
        androidx.fragment.app.FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStackImmediate();
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
        BatteryMonitorService.startServiceCompat(this, BatteryMonitorService.START_SOURCE_APP);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_about) {
            startActivity(new Intent(this, AboutActivity.class));
            return true;
        }
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
            getString(R.string.theme_dark),
            getString(R.string.theme_oled_black),
            getString(R.string.theme_eink_light),
            getString(R.string.theme_eink_dark)
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
            case ThemeHelper.MODE_OLED:
                checkedItem = 3;
                break;
            case ThemeHelper.MODE_EINK_LIGHT:
                checkedItem = 4;
                break;
            case ThemeHelper.MODE_EINK_DARK:
                checkedItem = 5;
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
                } else if (which == 3) {
                    mode = ThemeHelper.MODE_OLED;
                } else if (which == 4) {
                    mode = ThemeHelper.MODE_EINK_LIGHT;
                } else if (which == 5) {
                    mode = ThemeHelper.MODE_EINK_DARK;
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

