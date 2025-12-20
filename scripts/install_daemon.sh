#!/bin/bash

# Magic Daemon安装脚本

DAEMON_PATH="/data/local/tmp/battery_service_daemon"
SOCKET_PATH="/data/local/tmp/battery_service.sock"
LOG_PATH="/data/local/tmp/battery_service.log"

echo "Installing Battery Service Daemon..."

# 检查是否有root权限
if [ "$(id -u)" != "0" ]; then
    echo "Error: This script requires root privileges"
    exit 1
fi

# 停止旧进程
echo "Stopping old daemon..."
pkill -f battery_service_daemon 2>/dev/null || true
rm -f $SOCKET_PATH

# 备份旧日志
if [ -f "$LOG_PATH" ]; then
    mv $LOG_PATH "${LOG_PATH}.old"
fi

# 复制新daemon
echo "Installing new daemon..."
cp magic_daemon/build/battery_service_daemon $DAEMON_PATH
chmod 755 $DAEMON_PATH

# 设置root权限
echo "Setting root permissions..."
chown root:root $DAEMON_PATH
chmod 4755 $DAEMON_PATH

# 验证安装
if [ ! -f "$DAEMON_PATH" ]; then
    echo "Error: Failed to install daemon"
    exit 1
fi

if [ ! -x "$DAEMON_PATH" ]; then
    echo "Error: Daemon is not executable"
    exit 1
fi

# 启动daemon
echo "Starting daemon..."
nohup $DAEMON_PATH > /dev/null 2>&1 &

# 检查启动状态
sleep 2
if [ -S "$SOCKET_PATH" ]; then
    echo "Battery Service Daemon installed and started successfully"
    echo "Socket: $SOCKET_PATH"
    echo "Log: $LOG_PATH"
    echo "Process: $(pgrep -f battery_service_daemon)"
else
    echo "Error: Failed to start daemon"
    echo "Check log: $LOG_PATH"
    exit 1
fi