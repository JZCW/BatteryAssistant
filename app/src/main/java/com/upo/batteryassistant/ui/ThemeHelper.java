package com.upo.batteryassistant.ui;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import com.upo.batteryassistant.R;

/**
 * 统一管理应用主题模式（跟随系统 / 常规浅色 / 深色），便于以后扩展更多主题。
 */
public class ThemeHelper {
    private static final String PREF_NAME = "ba_theme_prefs";
    private static final String KEY_THEME_MODE = "theme_mode";

    public static final int MODE_FOLLOW_SYSTEM = 0;
    public static final int MODE_LIGHT = 1;
    public static final int MODE_DARK = 2;
    public static final int MODE_OLED = 4;
    public static final int MODE_EINK_LIGHT = 5;
    public static final int MODE_EINK_DARK = 6;

    public static void applySavedTheme(@NonNull Context context) {
        int mode = getSavedThemeMode(context);
        applyThemeMode(mode);
    }

    public static void saveThemeMode(@NonNull Context context, int mode) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().putInt(KEY_THEME_MODE, mode).apply();
        applyThemeMode(mode);
    }

    public static int getSavedThemeMode(@NonNull Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        int savedMode = sp.getInt(KEY_THEME_MODE, MODE_FOLLOW_SYSTEM);
        return savedMode == 3 ? MODE_LIGHT : savedMode;
    }

    public static int getThemeResId(@NonNull Context context) {
        int mode = getSavedThemeMode(context);
        switch (mode) {
            case MODE_LIGHT:
                return R.style.Theme_BatteryAssistant_Light;
            case MODE_OLED:
                return R.style.Theme_BatteryAssistant_Oled;
            case MODE_EINK_LIGHT:
                return R.style.Theme_BatteryAssistant_EinkLight;
            case MODE_EINK_DARK:
                return R.style.Theme_BatteryAssistant_EinkDark;
            case MODE_DARK:
            case MODE_FOLLOW_SYSTEM:
            default:
                return R.style.Theme_BatteryAssistant;
        }
    }

    private static void applyThemeMode(int mode) {
        switch (mode) {
            case MODE_LIGHT:
            case MODE_EINK_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case MODE_DARK:
            case MODE_OLED:
            case MODE_EINK_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case MODE_FOLLOW_SYSTEM:
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
        }
    }
}
