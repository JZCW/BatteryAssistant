# BatteryAssistant

在 Android 上实现 SONY 式电池保养——让电池在你需要的时间刚好充满，践行慢充理念，保护电池寿命。

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

### 前置条件

在安装前，请确认：

- [ ] 设备已安装 KernelSU（推荐）或 Magisk 并获取 root 权限
- [ ] 了解 root 权限的风险，并信任本应用

### 安装步骤

1. 从 [Releases](../../releases) 页面下载最新 APK
2. 在设备上安装 APK
3. 首次启动时，依次完成以下授权：

   | 权限 | 操作路径 |
   |------|--------|
   | 通知权限 | 应用启动时弹窗授权 |
   | 关闭电池优化 | 系统设置 → 应用 → BatteryAssistant → 电池 → 不限制 |
   | 允许后台运行 | 系统设置 → 应用 → BatteryAssistant → 电池 → 允许后台活动 |
   | Root 权限 | KernelSU / Magisk 管理器弹窗授权 |

4. 返回应用，等待监控服务启动（通知栏出现持久通知即表示正常运行）

---

## 架构概述

本项目分为两个主要部分：

**Android 应用（Java）**
- `BatteryMonitorService`：前台服务，持续监控电池状态并触发历史记录
- `BatteryInfoManager`：从系统 API 和 daemon 采集电池数据
- `ChargeHistoryManager`：管理充放电事件的 SQLite 存储

**系统 Daemon（C++）**
- 以 root 权限运行，直接读写 `/sys/class/power_supply/` 下的系统节点，获取硬件层电池数据及控制充电参数
- 通过本地 Unix domain socket 与应用通信，数据格式为 JSON
- 包含调试版本（`batteryAssistant_debug`）和正式版本（`batteryAssistant`），以及 socket 代理（`batteryProxy`）

```
Android App  ←──Unix socket (JSON)──→  Daemon (root)  ←──→  /sys/class/power_supply/
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
# Debug 版本
bash build_debug.sh
# Release 版本
bash build_release.sh
```

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

- **设备兼容性**：仅在 Nothing Phone 3 上完整验证，其他设备的电池驱动节点差异较大，可能功能异常或完全无法使用
- **功能未完成**：智能充电控制和温度保护尚未实现，见上方功能列表
- **服务稳定性**：部分深度定制 ROM 可能因进程冻结（cgroup freezer）导致后台服务中断
- **安全提示**：本应用需要 root 权限，请仅在充分了解风险后使用；所有数据仅存储在本地设备上

---

## 常见问题

**Q：为什么必须要 root 权限？**

充电电流等参数通过 `/sys/class/power_supply/` 下的系统文件控制，普通应用无法访问，必须以 root 权限操作。

**Q：为什么推荐 KernelSU 而不是 Magisk？**

daemon 在 KernelSU 环境下经过完整测试。Magisk 应兼容，但未进行完整验证。

**Q：我的设备不是 Nothing Phone 3，可以用吗？**

可以尝试安装，但不保证正常工作。不同厂商的电池驱动节点路径和字段差异显著，多机型适配在后续计划中。

**Q：后台服务有时自动停止，怎么办？**

请确认已完成安装步骤中列出的全部授权，特别是关闭电池优化和允许后台运行。部分 ROM 即使完成上述设置仍可能冻结进程。

**Q：如何完全卸载？**

在系统设置中卸载 APK 即可。daemon 进程会随应用卸载自动停止；若 daemon 以 Magisk/KernelSU 模块形式安装，请同时在对应管理器中移除模块。

---

## 许可证

本项目以 [GNU General Public License v3.0](LICENSE) 授权。衍生项目和修改版本必须以相同协议开源。

---

## 致谢

- 项目灵感来源：SONY Xperia 的电池保养功能（Battery Care）
- [jsoncpp](https://github.com/open-source-parsers/jsoncpp) — App 与 daemon 之间的 JSON 通信库
- [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart) — 图表展示库
