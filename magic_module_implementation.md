# Magic模块实现指南

## 核心文件结构

```
BatteryAssistant/
├── app/src/main/java/com/upo/batteryassistant/
│   ├── service/
│   │   └── BatteryServiceConnector.java    # Android端连接器
│   └── manager/
│       └── BatteryInfoManager.java         # 修改后的管理器
├── magic_daemon/                          # 独立C++项目
│   ├── src/
│   │   ├── main.cpp                       # 主程序
│   │   ├── data_collector.cpp              # 数据采集器
│   │   ├── cache_manager.cpp              # 缓存管理器
│   │   ├── charge_controller.cpp           # 充电控制器
│   │   ├── socket_server.cpp               # Socket服务器
│   │   └── message_handler.cpp            # 消息处理器
│   └── CMakeLists.txt                     # 构建配置
└── scripts/
    └── install_daemon.sh                   # 安装脚本
```

## 1. BatteryServiceConnector.java

```java
package com.upo.batteryassistant.service;

import android.util.Log;
import org.json.JSONObject;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class BatteryServiceConnector {
    private static final String TAG = "BatteryServiceConnector";
    private static final String SOCKET_PATH = "/data/local/tmp/battery_service.sock";
    private static final int TIMEOUT = 3000;
    
    private Socket socket;
    private DataOutputStream outputStream;
    private DataInputStream inputStream;
    private boolean isConnected = false;
    
    public interface BatteryDataListener {
        void onBatteryDataChanged(BatteryData data);
    }
    
    private BatteryDataListener listener;
    
    public void setBatteryDataListener(BatteryDataListener listener) {
        this.listener = listener;
    }
    
    public boolean connect() {
        try {
            socket = new Socket();
            socket.connect(new java.net.UnixDomainSocketAddress(SOCKET_PATH), TIMEOUT);
            socket.setSoTimeout(TIMEOUT);
            
            outputStream = new DataOutputStream(socket.getOutputStream());
            inputStream = new DataInputStream(socket.getInputStream());
            
            isConnected = true;
            Log.i(TAG, "Connected to battery service daemon");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to connect", e);
            disconnect();
            return false;
        }
    }
    
    public void disconnect() {
        isConnected = false;
        try {
            if (outputStream != null) outputStream.close();
            if (inputStream != null) inputStream.close();
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
        
        socket = null;
        outputStream = null;
        inputStream = null;
    }
    
    public CompletableFuture<BatteryData> getBatteryStatus() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isConnected && !connect()) {
                    return null;
                }
                
                // 发送请求
                JSONObject request = new JSONObject();
                request.put("type", "get_battery_status");
                
                sendRequest(request.toString());
                
                // 接收响应
                String response = receiveResponse();
                if (response != null) {
                    JSONObject jsonResponse = new JSONObject(response);
                    if (jsonResponse.getBoolean("success")) {
                        return BatteryData.fromJson(jsonResponse.getJSONObject("data"));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to get battery status", e);
            }
            return null;
        });
    }
    
    public CompletableFuture<Boolean> setChargeThreshold(int startThreshold, int endThreshold) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!isConnected && !connect()) {
                    return false;
                }
                
                JSONObject request = new JSONObject();
                request.put("type", "set_charge_threshold");
                
                JSONObject config = new JSONObject();
                config.put("start_threshold", startThreshold);
                config.put("end_threshold", endThreshold);
                request.put("config", config);
                
                sendRequest(request.toString());
                
                String response = receiveResponse();
                if (response != null) {
                    JSONObject jsonResponse = new JSONObject(response);
                    return jsonResponse.getBoolean("success");
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to set charge threshold", e);
            }
            return false;
        });
    }
    
    private void sendRequest(String request) throws IOException {
        byte[] data = request.getBytes("UTF-8");
        outputStream.writeInt(data.length);
        outputStream.write(data);
        outputStream.flush();
    }
    
    private String receiveResponse() throws IOException {
        int length = inputStream.readInt();
        if (length <= 0 || length > 1024 * 1024) {
            throw new IOException("Invalid response length: " + length);
        }
        
        byte[] data = new byte[length];
        inputStream.readFully(data);
        return new String(data, "UTF-8");
    }
}
```

## 2. BatteryData.java (数据模型)

```java
package com.upo.batteryassistant.service;

import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

public class BatteryData {
    public long timestamp;
    public Map<String, String> battery = new HashMap<>();
    public Map<String, String> usb = new HashMap<>();
    public Map<String, String> wireless = new HashMap<>();
    
    public static BatteryData fromJson(JSONObject json) throws Exception {
        BatteryData data = new BatteryData();
        data.timestamp = json.getLong("timestamp");
        
        JSONObject battery = json.getJSONObject("battery");
        for (String key : battery.keys()) {
            data.battery.put(key, battery.getString(key));
        }
        
        if (json.has("usb")) {
            JSONObject usb = json.getJSONObject("usb");
            for (String key : usb.keys()) {
                data.usb.put(key, usb.getString(key));
            }
        }
        
        if (json.has("wireless")) {
            JSONObject wireless = json.getJSONObject("wireless");
            for (String key : wireless.keys()) {
                data.wireless.put(key, wireless.getString(key));
            }
        }
        
        return data;
    }
    
    public String getBatteryValue(String key) {
        return battery.get(key);
    }
    
    public String getUsbValue(String key) {
        return usb.get(key);
    }
    
    public String getWirelessValue(String key) {
        return wireless.get(key);
    }
    
    public int getCapacity() {
        String value = battery.get("capacity");
        return value != null ? Integer.parseInt(value) : -1;
    }
    
    public int getTemperature() {
        String value = battery.get("temp");
        return value != null ? Integer.parseInt(value) : -1;
    }
    
    public long getVoltageNow() {
        String value = battery.get("voltage_now");
        return value != null ? Long.parseLong(value) : -1;
    }
    
    public long getCurrentNow() {
        String value = battery.get("current_now");
        return value != null ? Long.parseLong(value) : -1;
    }
    
    public String getStatus() {
        return battery.get("status");
    }
    
    public boolean isUsbOnline() {
        String value = usb.get("online");
        return value != null && "1".equals(value);
    }
    
    public boolean isWirelessOnline() {
        String value = wireless.get("online");
        return value != null && "1".equals(value);
    }
}
```

## 3. 修改后的BatteryInfoManager.java

```java
package com.upo.batteryassistant.manager;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import com.upo.batteryassistant.data.BatteryInfo;
import com.upo.batteryassistant.service.BatteryData;
import com.upo.batteryassistant.service.BatteryServiceConnector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class BatteryInfoManager {
    private static BatteryInfoManager instance;
    private Context context;
    private BatteryServiceConnector serviceConnector;
    
    private BatteryInfoManager(Context context) {
        this.context = context.getApplicationContext();
        this.serviceConnector = new BatteryServiceConnector();
        this.serviceConnector.connect();
    }
    
    public static BatteryInfoManager getInstance(Context context) {
        if (instance == null) {
            synchronized (BatteryInfoManager.class) {
                if (instance == null) {
                    instance = new BatteryInfoManager(context);
                }
            }
        }
        return instance;
    }
    
    public BatteryInfo getCurrentBatteryInfo() {
        BatteryInfo info = new BatteryInfo();
        
        // 获取基础信息（从Android系统）
        fillBasicInfo(info);
        
        // 获取高级信息（从Battery Service）
        fillAdvancedInfo(info);
        
        return info;
    }
    
    private void fillBasicInfo(BatteryInfo info) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, filter);
        
        if (batteryStatus != null) {
            // 电量级别
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) {
                info.setLevel((int) (level * 100.0f / scale));
            }
            
            // 电压、温度等基础信息
            info.setVoltage(batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1));
            info.setTemperature(batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1));
            info.setStatus(batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1));
            info.setHealth(batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, -1));
            info.setPlugged(batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1));
        }
    }
    
    private void fillAdvancedInfo(BatteryInfo info) {
        try {
            BatteryData data = serviceConnector.getBatteryStatus().get(2, TimeUnit.SECONDS);
            if (data != null) {
                // 填充高级电池信息
                info.setAdvBattCapacity(data.getCapacity());
                info.setAdvBattTempDeciC(data.getTemperature());
                info.setAdvBattVoltageNowUv(data.getVoltageNow());
                info.setAdvBattCurrentNowUa(data.getCurrentNow());
                info.setAdvBattStatusText(data.getStatus());
                
                // USB信息
                info.setAdvUsbOnline(data.isUsbOnline());
                if (data.isUsbOnline()) {
                    String voltage = data.getUsbValue("voltage_now");
                    if (voltage != null) {
                        info.setAdvUsbVoltageNowUv(Long.parseLong(voltage));
                    }
                    String current = data.getUsbValue("current_now");
                    if (current != null) {
                        info.setAdvUsbCurrentNowUa(Integer.parseInt(current));
                    }
                }
                
                // 无线充电信息
                info.setAdvWlsOnline(data.isWirelessOnline());
            }
        } catch (Exception e) {
            // 如果Battery Service不可用，忽略高级信息
        }
    }
    
    public CompletableFuture<Boolean> setChargeThreshold(int startThreshold, int endThreshold) {
        return serviceConnector.setChargeThreshold(startThreshold, endThreshold);
    }
    
    public void cleanup() {
        if (serviceConnector != null) {
            serviceConnector.disconnect();
        }
    }
}
```

## 4. Magic Daemon核心实现

### 4.1 main.cpp

```cpp
#include <iostream>
#include <signal.h>
#include <unistd.h>
#include "data_collector.h"
#include "cache_manager.h"
#include "socket_server.h"
#include "charge_controller.h"
#include "logger.h"

volatile sig_atomic_t running = 1;

void signalHandler(int signal) {
    running = 0;
}

int main() {
    signal(SIGINT, signalHandler);
    signal(SIGTERM, signalHandler);
    
    Logger::init("/data/local/tmp/battery_service.log");
    Logger::info("Battery Service Daemon starting...");
    
    try {
        // 检查root权限
        if (getuid() != 0) {
            Logger::error("Root permission required");
            return 1;
        }
        
        // 初始化组件
        CacheManager::getInstance();
        ChargeController::getInstance();
        
        // 启动数据采集器
        DataCollector collector;
        collector.start();
        
        // 启动Socket服务器
        SocketServer server("/data/local/tmp/battery_service.sock");
        if (!server.start()) {
            Logger::error("Failed to start socket server");
            return 1;
        }
        
        Logger::info("Battery Service Daemon started successfully");
        
        // 主循环
        while (running) {
            server.handleConnections();
            usleep(100000); // 100ms
        }
        
        // 清理
        collector.stop();
        server.stop();
        Logger::info("Battery Service Daemon stopped");
        
    } catch (const std::exception& e) {
        Logger::error("Exception: " + std::string(e.what()));
        return 1;
    }
    
    return 0;
}
```

### 4.2 data_collector.cpp

```cpp
#include "data_collector.h"
#include "cache_manager.h"
#include "logger.h"
#include <fstream>
#include <thread>
#include <chrono>
#include <filesystem>

DataCollector::DataCollector() : running(false) {
    // 初始化文件路径列表
    batteryFiles = {
        "/sys/class/power_supply/battery/capacity",
        "/sys/class/power_supply/battery/temp",
        "/sys/class/power_supply/battery/voltage_now",
        "/sys/class/power_supply/battery/current_now",
        "/sys/class/power_supply/battery/status",
        "/sys/class/power_supply/battery/health",
        "/sys/class/power_supply/battery/charge_counter",
        "/sys/class/power_supply/battery/charge_full",
        "/sys/class/power_supply/battery/cycle_count"
    };
    
    usbFiles = {
        "/sys/class/power_supply/usb/online",
        "/sys/class/power_supply/usb/voltage_now",
        "/sys/class/power_supply/usb/current_now"
    };
    
    wirelessFiles = {
        "/sys/class/power_supply/wireless/online",
        "/sys/class/power_supply/wireless/voltage_now",
        "/sys/class/power_supply/wireless/current_now"
    };
}

DataCollector::~DataCollector() {
    stop();
}

void DataCollector::start() {
    if (running) return;
    
    running = true;
    collectorThread = std::thread(&DataCollector::collectLoop, this);
    Logger::info("DataCollector started");
}

void DataCollector::stop() {
    if (!running) return;
    
    running = false;
    if (collectorThread.joinable()) {
        collectorThread.join();
    }
    Logger::info("DataCollector stopped");
}

void DataCollector::collectLoop() {
    while (running) {
        auto startTime = std::chrono::steady_clock::now();
        
        try {
            BatteryData data = readAllFiles();
            CacheManager::getInstance().updateBatteryData(data);
        } catch (const std::exception& e) {
            Logger::error("Data collection error: " + std::string(e.what()));
        }
        
        // 固定1秒周期
        auto elapsed = std::chrono::steady_clock::now() - startTime;
        auto sleepTime = std::chrono::milliseconds(1000) - elapsed;
        if (sleepTime.count() > 0) {
            std::this_thread::sleep_for(sleepTime);
        }
    }
}

BatteryData DataCollector::readAllFiles() {
    BatteryData data;
    
    // 读取电池文件
    for (const auto& file : batteryFiles) {
        std::string content = readFile(file);
        if (!content.empty()) {
            data.battery[getFileName(file)] = content;
        }
    }
    
    // 读取USB文件
    for (const auto& file : usbFiles) {
        std::string content = readFile(file);
        if (!content.empty()) {
            data.usb[getFileName(file)] = content;
        }
    }
    
    // 读取无线充电文件
    for (const auto& file : wirelessFiles) {
        std::string content = readFile(file);
        if (!content.empty()) {
            data.wireless[getFileName(file)] = content;
        }
    }
    
    data.timestamp = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count();
    
    return data;
}

std::string DataCollector::readFile(const std::string& path) {
    std::ifstream file(path);
    if (!file.is_open()) {
        return "";
    }
    
    std::string content;
    std::getline(file, content);
    file.close();
    
    // 去除空白字符
    content.erase(0, content.find_first_not_of(" \t\n\r"));
    content.erase(content.find_last_not_of(" \t\n\r") + 1);
    
    return content;
}

std::string DataCollector::getFileName(const std::string& path) {
    size_t pos = path.find_last_of('/');
    return (pos != std::string::npos) ? path.substr(pos + 1) : path;
}
```

### 4.3 socket_server.cpp

```cpp
#include "socket_server.h"
#include "cache_manager.h"
#include "charge_controller.h"
#include "logger.h"
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <fcntl.h>
#include <cstring>

SocketServer::SocketServer(const std::string& socketPath) 
    : socketPath(socketPath), serverFd(-1) {
}

SocketServer::~SocketServer() {
    stop();
}

bool SocketServer::start() {
    // 删除旧的socket文件
    unlink(socketPath.c_str());
    
    // 创建socket
    serverFd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (serverFd < 0) {
        Logger::error("Failed to create socket");
        return false;
    }
    
    // 设置非阻塞
    int flags = fcntl(serverFd, F_GETFL, 0);
    fcntl(serverFd, F_SETFL, flags | O_NONBLOCK);
    
    // 绑定地址
    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, socketPath.c_str(), sizeof(addr.sun_path) - 1);
    
    if (bind(serverFd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        Logger::error("Failed to bind socket");
        close(serverFd);
        return false;
    }
    
    // 开始监听
    if (listen(serverFd, 5) < 0) {
        Logger::error("Failed to listen on socket");
        close(serverFd);
        return false;
    }
    
    // 设置权限
    chmod(socketPath.c_str(), 0666);
    
    Logger::info("Socket server started on " + socketPath);
    return true;
}

void SocketServer::stop() {
    if (serverFd >= 0) {
        close(serverFd);
        serverFd = -1;
    }
    unlink(socketPath.c_str());
    Logger::info("Socket server stopped");
}

void SocketServer::handleConnections() {
    fd_set readFds;
    struct timeval timeout;
    
    FD_ZERO(&readFds);
    FD_SET(serverFd, &readFds);
    
    timeout.tv_sec = 0;
    timeout.tv_usec = 100000; // 100ms
    
    int result = select(serverFd + 1, &readFds, nullptr, nullptr, &timeout);
    if (result < 0) {
        if (errno != EINTR) {
            Logger::error("Select error: " + std::string(strerror(errno)));
        }
        return;
    }
    
    if (result > 0 && FD_ISSET(serverFd, &readFds)) {
        // 接受新连接
        struct sockaddr_un clientAddr;
        socklen_t clientLen = sizeof(clientAddr);
        int clientFd = accept(serverFd, (struct sockaddr*)&clientAddr, &clientLen);
        
        if (clientFd >= 0) {
            // 处理客户端请求
            handleClient(clientFd);
        }
    }
}

void SocketServer::handleClient(int clientFd) {
    try {
        // 读取请求长度
        int length;
        if (read(clientFd, &length, sizeof(length)) != sizeof(length)) {
            throw std::runtime_error("Failed to read request length");
        }
        
        // 验证长度
        if (length <= 0 || length > 1024 * 1024) {
            throw std::runtime_error("Invalid request length: " + std::to_string(length));
        }
        
        // 读取请求数据
        std::string requestData(length, '\0');
        int bytesRead = 0;
        while (bytesRead < length) {
            int n = read(clientFd, &requestData[bytesRead], length - bytesRead);
            if (n <= 0) {
                throw std::runtime_error("Failed to read request data");
            }
            bytesRead += n;
        }
        
        // 处理请求
        std::string response = processRequest(requestData);
        
        // 发送响应
        int responseLength = response.length();
        write(clientFd, &responseLength, sizeof(responseLength));
        write(clientFd, response.c_str(), responseLength);
        
    } catch (const std::exception& e) {
        Logger::error("Error handling client: " + std::string(e.what()));
    }
    
    close(clientFd);
}

std::string SocketServer::processRequest(const std::string& requestData) {
    try {
        // 解析JSON请求
        nlohmann::json request = nlohmann::json::parse(requestData);
        std::string type = request["type"];
        
        nlohmann::json response;
        response["success"] = true;
        
        if (type == "get_battery_status") {
            BatteryData data = CacheManager::getInstance().getBatteryData();
            response["data"] = data.toJson();
        } else if (type == "set_charge_threshold") {
            auto config = request["config"];
            int startThreshold = config["start_threshold"];
            int endThreshold = config["end_threshold"];
            
            bool success = ChargeController::getInstance().setChargeThreshold(startThreshold, endThreshold);
            response["success"] = success;
            response["data"]["applied"] = success;
        } else {
            response["success"] = false;
            response["error"] = "Unknown request type: " + type;
        }
        
        return response.dump();
        
    } catch (const std::exception& e) {
        nlohmann::json response;
        response["success"] = false;
        response["error"] = e.what();
        return response.dump();
    }
}
```

## 5. 安装脚本

### 5.1 install_daemon.sh

```bash
#!/bin/bash

DAEMON_PATH="/data/local/tmp/battery_service_daemon"
SOCKET_PATH="/data/local/tmp/battery_service.sock"
LOG_PATH="/data/local/tmp/battery_service.log"

echo "Installing Battery Service Daemon..."

# 停止旧进程
echo "Stopping old daemon..."
pkill -f battery_service_daemon
rm -f $SOCKET_PATH

# 复制新daemon
echo "Installing new daemon..."
cp battery_service_daemon $DAEMON_PATH
chmod 755 $DAEMON_PATH

# 设置root权限
echo "Setting root permissions..."
chown root:root $DAEMON_PATH
chmod 4755 $DAEMON_PATH

# 清理旧日志
if [ -f "$LOG_PATH" ]; then
    mv $LOG_PATH "${LOG_PATH}.old"
fi

# 启动daemon
echo "Starting daemon..."
$DAEMON_PATH

# 检查启动状态
sleep 2
if [ -S "$SOCKET_PATH" ]; then
    echo "Battery Service Daemon installed and started successfully"
    echo "Socket: $SOCKET_PATH"
    echo "Log: $LOG_PATH"
else
    echo "Failed to start daemon"
    exit 1
fi
```

## 6. 构建配置

### 6.1 CMakeLists.txt

```cmake
cmake_minimum_required(VERSION 3.18.1)
project("battery_service_daemon")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -O2")

# 查找依赖
find_package(PkgConfig REQUIRED)
pkg_check_modules(NLOHMANN_JSON REQUIRED nlohmann_json)

# 源文件
set(SOURCES
    src/main.cpp
    src/data_collector.cpp
    src/cache_manager.cpp
    src/socket_server.cpp
    src/charge_controller.cpp
    src/logger.cpp
)

# 头文件目录
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

# 创建可执行文件
add_executable(battery_service_daemon ${SOURCES})

# 链接库
target_link_libraries(battery_service_daemon 
    pthread
    dl
    ${NLOHMANN_JSON_LIBRARIES}
)

# 编译选项
target_compile_options(battery_service_daemon PRIVATE ${NLOHMANN_JSON_CFLAGS_OTHER})

# 安装规则
install(TARGETS battery_service_daemon 
    DESTINATION ${CMAKE_INSTALL_PREFIX}/bin
)
```

## 7. 集成步骤

1. **编译Magic Daemon**
   ```bash
   cd magic_daemon
   mkdir build && cd build
   cmake ..
   make
   ```

2. **安装Daemon**
   ```bash
   sudo ./install_daemon.sh
   ```

3. **修改Android应用**
   - 添加BatteryServiceConnector到项目
   - 修改BatteryInfoManager使用新接口
   - 移除对RootUtil的直接依赖

4. **测试集成**
   - 验证Daemon正常运行
   - 测试应用获取电池信息
   - 测试充电控制功能

这个实现指南提供了完整的核心代码，专注于功能实现，去除了不必要的复杂性。