# BatteryAssistant

在其它设备上实现SONY的电池保养功能——让电池在你需要的时间刚好充满，践行慢充理念，保护电池寿命。

> [!WARNING]
> **本项目处于 Alpha 阶段。** 核心功能（智能充电控制、温度保护）尚未完成；当前仅在 **Nothing Phone 3** 上完整测试。请勿在重要设备上作为主力工具使用。

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

---

## 功能

| 状态 | 功能 | 说明 |
|------|------|------|
| ✅ 已实现 | **实时电池信息** | 显示当前电量、电压、温度、充电状态、快充类型等详细信息 |
| ✅ 已实现 | **充放电历史记录** | 自动记录每次充/放电事件的完整状态快照，追踪电池使用情况 |
| 🚧 计划中 | **智能充电控制** | 指定充满目标时间，自动以最低必要电流完成充电 |
| 🚧 计划中 | **温度保护** | 高温或低温环境下自动调整充电策略，防止电池损耗 |

### 系统要求

- Android 15+（API 35）
- **KernelSU**（推荐）或 Magisk
- Nothing Phone 3（当前唯一经过完整测试的设备）

---

## 安装

### 安装步骤

1. 安装模块 `batteryAssistant.zip`
2. 重启设备，使模块生效
3. 安装 APK
4. 首次启动时，依次完成以下授权：

   | 权限 | 操作路径 |
   |------|--------|
   | 通知权限 | 应用启动时弹窗授权 |
   | 关闭电池优化 | 系统设置 → 应用 → BatteryAssistant → 电池 → 不限制 |
   | 允许后台运行 | 系统设置 → 应用 → BatteryAssistant → 电池 → 允许后台活动 |
   | Root 权限 | KernelSU / Magisk 管理器授权 |

5. 返回应用，等待监控服务启动（通知栏出现持久通知即表示正常运行）

---

## 架构概述

本项目分为两个主要部分：

**Android 应用（Java）**
- `BatteryMonitorService`：主服务，监控电池状态并调整充电速率
- `BatteryInfoManager`：从系统 API 和 daemon 采集电池数据
- `ChargeHistoryManager`：管理充放电历史数据，使用 SQLite

**系统 Daemon（C++）**
- 以 root 权限运行，直接读写 `/sys/class/power_supply/` 下的系统节点，获取硬件层电池数据及控制充电参数
- 通过本地 Unix domain socket 与应用通信，数据格式为 JSON
- `batteryAssistant`为独立服务，不随App退出而停止
- `batteryProxy`桥接App和Daemon，并且负责App保活，当App被用户主动结束后会退出

```
Android App  ←→ Bridge(root) ←→ Daemon (root)  ←→  /sys/class/power_supply/
```

---

## 开发者指南

### 编译环境

| 工具 | 版本要求 |
|------|--------|
| JDK | 11 |
| Android SDK | API 35+ |
| NDK | 任意支持 C++17 的版本 |
| CMake | 3.18.1+ |

### 编译 App

```bash
cd BatteryAssistant
./gradlew assembleDebug
```

输出 APK 位于 `app/build/outputs/apk/debug/`。

### 编译 Daemon

```bash
cd magisk_daemon
bash build_release.sh
```

输出 ZIP 位于 `magisk_daemon/out/artifacts/`。

### 项目结构

```
BatteryAssistant/
├── app/                    # Android 应用（Java）
│   └── src/main/
│       ├── java/           # 应用逻辑
│       └── res/            # 界面资源
└── magisk_daemon/          # 系统 Daemon（C++17）
    ├── src/                # 源码
    ├── include/            # 头文件
    ├── CMakeLists.txt
    ├── build_debug.sh
    └── build_release.sh
```

---

## 已知限制

- **设备兼容性**：仅在 Nothing Phone 3 上完整测试，其他设备的电池驱动节点差异较大，可能功能异常或完全无法使用
- **功能未完成**：智能充电控制和温度保护尚未实现，见上方功能列表
- **服务稳定性**：部分深度定制 ROM 可能因进程冻结（cgroup freezer）导致后台服务中断
- **安全提示**：本应用需要 root 权限，请仅在充分了解风险后使用

---

## 许可证

本项目以 [GNU General Public License v3.0](LICENSE) 授权。衍生项目和修改版本必须以相同协议开源。

---

## 致谢

- 项目灵感来源：SONY Xperia 的电池保养功能
- [jsoncpp](https://github.com/open-source-parsers/jsoncpp) — App 与 daemon 之间的 JSON 通信库
- [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) — 图表展示库
