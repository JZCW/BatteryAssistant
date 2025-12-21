# Magic Daemon - Battery Service Daemon

这是一个用于 Android 设备的电池服务守护进程，提供电池数据采集、充电控制和 Socket 通信功能。

## 系统要求

- **Android 15+ (API 35+)**
- **ARM64 架构 (arm64-v8a)**
- Root 权限
- 支持 FLEXIBLE_PAGE_SIZES 的设备 (Android 16+ 特性)

## 构建要求

- Android NDK r29 (29.0.14206865)
- CMake 3.18.1 或更高版本
- Git (用于下载 jsoncpp 依赖)

## 构建方法

### 桌面环境构建 (仅用于开发测试)

```bash
mkdir build && cd build
cmake ..
make
```

### Android NDK 构建 (生产环境)

使用提供的构建脚本：

```bash
./build_android.sh
```

脚本已配置为：
- 目标架构：arm64-v8a (ARM64)
- Android API：35+ (使用 NDK 支持的最新版本)
- 构建类型：Release
- 为 Android 16+ 的 FLEXIBLE_PAGE_SIZES 特性做准备

或者手动构建：

```bash
export ANDROID_NDK_ROOT="/cache/user/android/sdk/ndk/29.0.14206865"
mkdir build_android && cd build_android

cmake .. \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_ROOT/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="arm64-v8a" \
    -DANDROID_PLATFORM="latest" \
    -DCMAKE_BUILD_TYPE=Release

make -j$(nproc)
```

## 功能特性

- **电池数据采集**: 从 sysfs 读取电池、USB 和无线充电信息
- **充电控制**: 支持充电阈值、限制和开关控制
- **Socket 通信**: 提供 JSON 格式的 API 接口
- **缓存管理**: 高效的内存数据缓存
- **日志系统**: 详细的运行日志记录

## 使用方法

1. 将构建好的可执行文件推送到 Android 设备：
   ```bash
   adb push build_android/battery_service_daemon /data/local/tmp/
   ```

2. 设置执行权限：
   ```bash
   adb shell chmod 755 /data/local/tmp/battery_service_daemon
   ```

3. 以 root 权限运行：
   ```bash
   adb shell su -c "/data/local/tmp/battery_service_daemon"
   ```

**注意**:
- 确保设备运行 Android 15+ 并且已获得 root 权限
- 虽然当前构建针对 Android 15，但已为 Android 16+ 的 FLEXIBLE_PAGE_SIZES 特性做好准备
- 在支持 Android 16+ 的设备上运行时，将自动利用新的内存管理特性

## Socket API

守护进程会在 `/data/local/tmp/battery_service.sock` 创建 Unix Socket 服务器。

### 请求格式

```json
{
  "type": "get_battery_status"
}
```

### 支持的请求类型

- `get_battery_status`: 获取当前电池状态
- `set_charge_threshold`: 设置充电阈值
- `set_charge_limit`: 设置充电限制
- `enable_charging`: 启用/禁用充电

## 日志

日志文件位置：`/data/local/tmp/battery_service.log`

## 依赖库

- jsoncpp: JSON 解析和生成 (通过 FetchContent 自动下载)
- pthread: 多线程支持
- dl: 动态链接库支持

## 故障排除

1. **权限问题**: 确保以 root 权限运行
2. **设备兼容性**: 检查 `/sys/class/power_supply/` 下的文件
3. **Socket 连接**: 确保 `/data/local/tmp/` 目录可写

## 构建输出

- 桌面环境 (开发测试): `build/battery_service_daemon`
- Android ARM64 (生产环境): `build_android/battery_service_daemon`

构建的可执行文件是针对 Android 15+ (API 35+) 的 ARM64 架构优化的，并包含对 Android 16+ FLEXIBLE_PAGE_SIZES 特性的支持。