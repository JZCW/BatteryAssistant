package com.upo.batteryassistant.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.upo.batteryassistant.service.BatteryMonitorService;

/**
 * 开机自启动广播接收器
 * 监听系统开机完成广播，自动启动电池监控服务
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()) ||
            Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction())) {

            Log.i(TAG, "收到广播: " + intent.getAction());
            BatteryMonitorService.startServiceCompat(context, BatteryMonitorService.START_SOURCE_BOOT);
        }
    }
}

