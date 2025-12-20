#!/bin/bash

# Magic Daemon构建脚本

set -e

DAEMON_NAME="battery_service_daemon"
BUILD_DIR="magic_daemon/build"
INSTALL_DIR="/data/local/tmp"

echo "Building Magic Daemon..."

# 创建构建目录
mkdir -p $BUILD_DIR
cd $BUILD_DIR

# 配置CMake
echo "Configuring CMake..."
cmake .. -DCMAKE_BUILD_TYPE=Release

# 编译
echo "Compiling..."
make -j$(nproc)

# 检查编译结果
if [ ! -f "$DAEMON_NAME" ]; then
    echo "Build failed: $DAEMON_NAME not found"
    exit 1
fi

echo "Build successful: $DAEMON_NAME"
echo "Binary location: $BUILD_DIR/$DAEMON_NAME"

# 可选：安装到系统目录
if [ "$1" = "install" ]; then
    echo "Installing to $INSTALL_DIR..."
    cp $DAEMON_NAME $INSTALL_DIR/
    chmod 755 $INSTALL_DIR/$DAEMON_NAME
    echo "Installed to $INSTALL_DIR/$DAEMON_NAME"
fi