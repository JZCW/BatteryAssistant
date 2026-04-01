#ifndef CONFIG_H
#define CONFIG_H

// 所有运行时路径集中定义于此，修改部署路径只需改这一个文件
namespace Config {
    // 日志与 PID 文件路径
    constexpr const char* DAEMON_LOG_PATH    = "/data/local/tmp/battery_service.log";
    constexpr const char* BRIDGE_LOG_PATH    = "/data/local/tmp/battery_proxy.log";
    constexpr const char* BRIDGE_PID_PATH    = "/data/local/tmp/batteryProxy.pid";

    // 守护进程可执行文件路径
    constexpr const char* DAEMON_BINARY_PATH = "/data/adb/modules/batteryAssistant/batteryAssistant";

    // App配置
    constexpr const char* APP_PACKAGE = "com.upo.batteryassistant";
    constexpr const char* APP_SERVICE = "com.upo.batteryassistant/.service.BatteryMonitorService";

    // 电池相关 sysfs / procfs 路径
    constexpr const char* BATTERY_STATUS_PATH = "/sys/class/power_supply/battery/status";
    constexpr const char* SCENARIO_FCC_PATH   = "/proc/charger/scenario_fcc";
}

#endif // CONFIG_H
