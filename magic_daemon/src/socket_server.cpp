#include "socket_server.h"
#include "cache_manager.h"
#include "charge_controller.h"
#include "logger.h"
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/stat.h>
#include <unistd.h>
#include <fcntl.h>
#include <cstring>
#include <iostream>

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
        LOG_ERROR("Failed to create socket");
        return false;
    }
    
    // 绑定地址
    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, socketPath.c_str(), sizeof(addr.sun_path) - 1);
    
    if (bind(serverFd, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
        LOG_ERROR("Failed to bind socket");
        close(serverFd);
        return false;
    }
    
    // 开始监听
    if (listen(serverFd, 5) < 0) {
        LOG_ERROR("Failed to listen on socket");
        close(serverFd);
        return false;
    }
    
    // 设置权限
    chmod(socketPath.c_str(), 0666);
    
    running = true;
    serverThread = std::thread(&SocketServer::runServer, this);
    
    LOG_INFO("Socket server started on " + socketPath);
    return true;
}

void SocketServer::stop() {
    running = false;
    
    if (serverFd >= 0) {
        close(serverFd);
        serverFd = -1;
    }
    
    if (serverThread.joinable()) {
        serverThread.join();
    }
    
    unlink(socketPath.c_str());
    LOG_INFO("Socket server stopped");
}

void SocketServer::runServer() {
    while (running) {
        // 阻塞等待连接，零CPU开销
        struct sockaddr_un clientAddr;
        socklen_t clientLen = sizeof(clientAddr);
        int clientFd = accept(serverFd, (struct sockaddr*)&clientAddr, &clientLen);
        
        if (clientFd >= 0) {
            activeClients++;
            LOG_INFO("Client connected, active clients: " + std::to_string(activeClients));
            
            // 通知客户端连接回调
            if (clientConnectedCallback) {
                clientConnectedCallback();
            }
            
            // 立即处理客户端请求
            handleClient(clientFd);
            
        } else if (errno != EINTR) {
            LOG_ERROR("Accept error: " + std::string(strerror(errno)));
        }
    }
}

void SocketServer::handleClient(int clientFd) {
    try {
        // 读取请求
        std::string request = readRequest(clientFd);
        
        // 立即处理请求
        std::string response = processRequest(request);
        
        // 发送响应
        sendResponse(clientFd, response);
        
    } catch (const std::exception& e) {
        LOG_ERROR("Error handling client: " + std::string(e.what()));
    }
    
    close(clientFd);
    activeClients--;
    LOG_INFO("Client disconnected, active clients: " + std::to_string(activeClients));
    
    // 通知客户端断开回调
    if (clientDisconnectedCallback) {
        clientDisconnectedCallback();
    }
}

std::string SocketServer::readRequest(int clientFd) {
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
    
    return requestData;
}

void SocketServer::sendResponse(int clientFd, const std::string& response) {
    int responseLength = response.length();
    
    if (write(clientFd, &responseLength, sizeof(responseLength)) != sizeof(responseLength)) {
        throw std::runtime_error("Failed to write response length");
    }
    
    if (write(clientFd, response.c_str(), responseLength) != responseLength) {
        throw std::runtime_error("Failed to write response data");
    }
}

std::string SocketServer::processRequest(const std::string& requestData) {
    try {
        // 解析JSON请求
        Json::Value request;
        Json::Reader reader;
        if (!reader.parse(requestData, request)) {
            throw std::runtime_error("Invalid JSON request");
        }
        
        std::string type = request["type"].asString();
        
        // 控制命令：立即处理
        if (type == "set_charge_threshold" || 
            type == "set_charge_limit" || 
            type == "enable_charging") {
            
            return processControlCommand(request);
        }
        
        // 状态查询：从缓存获取
        if (type == "get_battery_status") {
            return processStatusQuery(request);
        }
        
        // 其他请求
        return processOtherRequest(request);
        
    } catch (const std::exception& e) {
        Json::Value response;
        response["success"] = false;
        response["error"] = e.what();
        Json::StreamWriterBuilder builder;
        return Json::writeString(builder, response);
    }
}

std::string SocketServer::processControlCommand(const Json::Value& request) {
    std::string type = request["type"].asString();
    Json::Value response;
    response["success"] = true;
    response["data"] = Json::Value(Json::objectValue);
    
    try {
        if (type == "set_charge_threshold") {
            const Json::Value& config = request["config"];
            int startThreshold = config["start_threshold"].asInt();
            int endThreshold = config["end_threshold"].asInt();
            
            // 立即应用充电阈值
            bool success = ChargeController::getInstance().setChargeThreshold(startThreshold, endThreshold);
            response["success"] = success;
            response["data"]["applied"] = success;
            response["data"]["timestamp"] = Json::Value::Int64(std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::system_clock::now().time_since_epoch()).count());
            
            LOG_INFO("Charge threshold set: " + std::to_string(startThreshold) + 
                      "-" + std::to_string(endThreshold) + " (success: " + 
                      std::to_string(success) + ")");
            
        } else if (type == "set_charge_limit") {
            int limit = request["limit"].asInt();
            bool success = ChargeController::getInstance().setChargeLimit(limit);
            response["success"] = success;
            response["data"]["applied"] = success;
            response["data"]["timestamp"] = Json::Value::Int64(std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::system_clock::now().time_since_epoch()).count());
            
            LOG_INFO("Charge limit set: " + std::to_string(limit) + 
                      " (success: " + std::to_string(success) + ")");
            
        } else if (type == "enable_charging") {
            bool enable = request["enable"].asBool();
            bool success = ChargeController::getInstance().enableCharging(enable);
            response["success"] = success;
            response["data"]["applied"] = success;
            response["data"]["timestamp"] = Json::Value::Int64(std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::system_clock::now().time_since_epoch()).count());
            
            LOG_INFO("Charging " + std::string(enable ? "enabled" : "disabled") + 
                      " (success: " + std::to_string(success) + ")");
        }
        
    } catch (const std::exception& e) {
        response["success"] = false;
        response["error"] = e.what();
    }
    
    Json::StreamWriterBuilder builder;
    return Json::writeString(builder, response);
}

std::string SocketServer::processStatusQuery(const Json::Value& request) {
    Json::Value response;
    response["success"] = true;
    
    // 从缓存获取最新数据
    BatteryData data = CacheManager::getInstance().getBatteryData();
    
    // 转换为JSON
    Json::Reader reader;
    Json::Value dataJson;
    reader.parse(data.toJson().toStyledString(), dataJson);
    response["data"] = dataJson;
    
    Json::StreamWriterBuilder builder;
    return Json::writeString(builder, response);
}

std::string SocketServer::processOtherRequest(const Json::Value& request) {
    Json::Value response;
    response["success"] = false;
    response["error"] = "Unknown request type: " + request["type"].asString();
    
    Json::StreamWriterBuilder builder;
    return Json::writeString(builder, response);
}