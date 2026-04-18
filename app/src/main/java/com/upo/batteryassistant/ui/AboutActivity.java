package com.upo.batteryassistant.ui;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.appbar.MaterialToolbar;
import com.upo.batteryassistant.R;
import com.upo.batteryassistant.ui.util.ThemeHelper;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 关于页面Activity
 * 展示应用版本和Daemon版本信息
 */
public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(ThemeHelper.getThemeResId(this));
        ThemeHelper.applySavedTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        setupActionBar();
        applyWindowInsets();
        
        setupVersionInfo();
    }

    private void setupActionBar() {
        MaterialToolbar toolbar = findViewById(R.id.about_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    private void applyWindowInsets() {
        View root = findViewById(R.id.about_root);
        View appBarContainer = findViewById(R.id.about_app_bar_container);
        View scrollView = findViewById(R.id.about_scroll_view);

        final int appBarLeft = appBarContainer.getPaddingLeft();
        final int appBarTop = appBarContainer.getPaddingTop();
        final int appBarRight = appBarContainer.getPaddingRight();
        final int appBarBottom = appBarContainer.getPaddingBottom();
        final int scrollLeft = scrollView.getPaddingLeft();
        final int scrollTop = scrollView.getPaddingTop();
        final int scrollRight = scrollView.getPaddingRight();
        final int scrollBottom = scrollView.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            appBarContainer.setPadding(
                appBarLeft + systemBars.left,
                appBarTop + systemBars.top,
                appBarRight + systemBars.right,
                appBarBottom
            );
            scrollView.setPadding(
                scrollLeft + systemBars.left,
                scrollTop,
                scrollRight + systemBars.right,
                scrollBottom + systemBars.bottom
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    private void setupVersionInfo() {
        TextView appVersionText = findViewById(R.id.about_app_version_value);
        TextView daemonVersionText = findViewById(R.id.about_daemon_version_value);

        // 获取应用版本
        String appVersion = getApplicationVersion();
        appVersionText.setText(appVersion != null ? appVersion : getString(R.string.common_unavailable));

        // 获取Daemon版本
        String daemonVersion = getDaemonVersion();
        daemonVersionText.setText(daemonVersion != null ? daemonVersion : getString(R.string.about_daemon_unavailable));
    }

    /**
     * 获取应用版本信息
     */
    private String getApplicationVersion() {
        try {
            PackageManager packageManager = getPackageManager();
            PackageInfo packageInfo = packageManager.getPackageInfo(getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 从 /data/adb/modules/batteryAssistant/module.prop 读取Daemon版本
     * 以root权限读取，获取 version 和 versionCode
     */
    private String getDaemonVersion() {
        String modulePropPath = "/data/adb/modules/batteryAssistant/module.prop";
        String version = null;
        String versionCode = null;

        try {
            // 使用 su -c cat 以root权限读取文件
            Process process = new ProcessBuilder("su", "-c", "cat " + modulePropPath).start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("version=")) {
                        version = line.substring("version=".length()).trim();
                    } else if (line.startsWith("versionCode=")) {
                        versionCode = line.substring("versionCode=".length()).trim();
                    }
                }
            }
            
            process.waitFor();
            
            // 格式化版本信息
            if (version != null && versionCode != null) {
                return version + " (" + versionCode + ")";
            } else if (version != null) {
                return version;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}
