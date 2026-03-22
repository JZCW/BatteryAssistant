package com.upo.batteryassistant.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.upo.batteryassistant.service.BatteryMonitorService;

public class ServiceRecoveryReceiver extends BroadcastReceiver {
    private static final String TAG = "ServiceRecoveryReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;
        Log.i(TAG, "onReceive action=" + action + ", lastHeartbeat=" + BatteryMonitorService.getLastHeartbeat(context));

        if (!BatteryMonitorService.ACTION_RECOVERY_CHECK.equals(action)) {
            return;
        }

        if (BatteryMonitorService.isServiceConsideredHealthy(context)) {
            Log.d(TAG, "Service is healthy, skip recovery start");
            return;
        }

        Log.w(TAG, "Service heartbeat stale or not running, starting recovery");
        BatteryMonitorService.startServiceCompat(context, BatteryMonitorService.START_SOURCE_ALARM);
    }
}
