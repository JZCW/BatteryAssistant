#!/bin/bash

# Android NDK 构建脚本 - 调试版本

# 固定配置 - 仅支持 arm64-v8a 和 Android 16+
ABI="arm64-v8a"
API=36  # Android 16 对应的 API 级别

# 设置路径
NDK_PATH="/cache/user/android/sdk/ndk/29.0.14206865"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$PROJECT_ROOT/build"

# 检查 NDK 是否存在
if [ ! -d "$NDK_PATH" ]; then
    echo "错误: NDK 路径不存在: $NDK_PATH"
    exit 1
fi

echo "构建配置 (调试版本):"
echo "  ABI: $ABI"
echo "  Android API: $API (Android 16+)"
echo "  NDK: $NDK_PATH"
echo ""

# 创建构建目录
mkdir -p "$BUILD_DIR"
cd "$BUILD_DIR"

# 配置 CMake - 使用 "latest" 平台以支持最新的 Android 特性
cmake .. \
    -DCMAKE_TOOLCHAIN_FILE="$NDK_PATH/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM="latest" \
    -DCMAKE_BUILD_TYPE=Release

# 构建调试版本
make batteryAssistant_debug -j$(nproc)

echo ""
echo "编译完成："
file "$BUILD_DIR/batteryAssistant_debug"

echo ""
echo "可执行文件位置: $BUILD_DIR/batteryAssistant_debug"
echo ""
echo "使用方法:"
echo "  adb push $BUILD_DIR/batteryAssistant_debug /data/local/tmp/"
echo "  adb shell"
echo "  su"
echo "  cd /data/local/tmp"
echo "  chmod +x batteryAssistant_debug"
echo "  ./batteryAssistant_debug"
