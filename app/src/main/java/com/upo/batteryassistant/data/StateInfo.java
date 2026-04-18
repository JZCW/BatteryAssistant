package com.upo.batteryassistant.data;

/**
 * 应用状态信息
 * 记录充电、屏幕、空闲等状态
 */
public class StateInfo {
    private boolean isCharging = false;
    private boolean isScreenOn = false;
    private boolean isIdle = false;

    public StateInfo() {}

    public boolean isCharging() {
        return isCharging;
    }

    public void setCharging(boolean charging) {
        isCharging = charging;
    }

    public boolean isScreenOn() {
        return isScreenOn;
    }

    public void setScreenOn(boolean screenOn) {
        isScreenOn = screenOn;
    }

    public boolean isIdle() {
        return isIdle;
    }

    public void setIdle(boolean idle) {
        isIdle = idle;
    }
    
    @Override
    public String toString() {
        return "StateInfo{" +
                "isCharging=" + isCharging +
                ", isScreenOn=" + isScreenOn +
                ", isIdle=" + isIdle +
                '}';
    }
}
