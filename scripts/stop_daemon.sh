#!/bin/bash

# Magic Daemon停止脚本

DAEMON_NAME="battery_service_daemon"
SOCKET_PATH="/data/local/tmp/battery_service.sock"

echo "Stopping Battery Service Daemon..."

# 检查是否有root权限
if [ "$(id -u)" != "0" ]; then
    echo "Error: This script requires root privileges"
    exit 1
fi

# 停止daemon进程
echo "Stopping daemon process..."
pkill -f $DAEMON_NAME

# 删除socket文件
echo "Removing socket file..."
rm -f $SOCKET_PATH

# 检查是否成功停止
sleep 1
if pgrep -f $DAEMON_NAME > /dev/null; then
    echo "Warning: Daemon process may still be running"
    echo "Process list:"
    pgrep -f $DAEMON_NAME
else
    echo "Battery Service Daemon stopped successfully"
fi