#!/bin/bash

set -euo pipefail

# Android NDK 构建脚本

# 固定配置 - 仅支持 arm64-v8a 和 Android 16+
ABI="arm64-v8a"
API=36  # Android 16 对应的 API 级别

# 设置路径
NDK_PATH="/cache/user/android/sdk/ndk/29.0.14206865"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT=$SCRIPT_DIR
BUILD_DIR="$PROJECT_ROOT/out/build"
ARTIFACT_DIR="$PROJECT_ROOT/out/artifacts"
PACKAGE_DIR="$PROJECT_ROOT/packaging/magisk"
MODULE_PROP="$PACKAGE_DIR/module.prop"

# 从 module.prop 文件中提取信息
id=$(grep '^id=' "$MODULE_PROP" | cut -d'=' -f2)
version=$(grep '^version=' "$MODULE_PROP" | cut -d'=' -f2)
zipFile="$ARTIFACT_DIR/${id}_${version}.zip"

# 检查 NDK 是否存在
if [ ! -d "$NDK_PATH" ]; then
    echo "错误: NDK 路径不存在: $NDK_PATH"
    exit 1
fi

cleanup() {
    rm -f "$PACKAGE_DIR/batteryAssistant" "$PACKAGE_DIR/batteryProxy"
}

trap cleanup EXIT

echo "构建配置:"
echo "  ABI: $ABI"
echo "  Android API: $API (Android 16+)"
echo "  NDK: $NDK_PATH"
echo ""

mkdir -p "$BUILD_DIR" "$ARTIFACT_DIR"

# 配置 CMake - 使用 latest 平台以支持最新的 Android 特性
cmake -S "$PROJECT_ROOT" -B "$BUILD_DIR" \
    -DCMAKE_TOOLCHAIN_FILE="$NDK_PATH/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI="$ABI" \
    -DANDROID_PLATFORM="latest" \
    -DCMAKE_BUILD_TYPE=Release

# 构建
cmake --build "$BUILD_DIR" --parallel "$(nproc)"

echo ""
echo "编译完成："

cp "$BUILD_DIR/batteryAssistant" "$PACKAGE_DIR/batteryAssistant"
cp "$BUILD_DIR/batteryProxy" "$PACKAGE_DIR/batteryProxy"

rm -f "$zipFile"
(cd "$PACKAGE_DIR" && 7z a "$zipFile" ./* > /dev/null)

echo "构建完成: $zipFile"



