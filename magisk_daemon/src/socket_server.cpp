#include "socket_server.h"
#include "data_collector.h"
#include "logger.h"
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/stat.h>
#include <unistd.h>
#include <fcntl.h>
#include <cstring>
#include <iostream>
#include <arpa/inet.h>

SocketServer::SocketServer(const std::string& socketName)
    : socketName(socketName), serverFd(-1) {
    // Store the original socket name, we'll add null byte when binding
}

SocketServer::~SocketServer() {
    stop();
}

bool SocketServer::start() {
    LOG_INFO("Starting socket server with abstract namespace: " + socketName);
    
    // Abstract namespace sockets don't create files, so no need to unlink
    LOG_INFO("Using abstract namespace socket: " + socketName);
    
    // 创建socket
    serverFd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (serverFd < 0) {
        LOG_ERROR("Failed to create socket: " + std::string(strerror(errno)));
        return false;
    }
    LOG_INFO("Socket created successfully, fd: " + std::to_string(serverFd));
    
    // 绑定地址 - for abstract namespace, the first byte should be null
    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    // For abstract namespace, first byte should be null, then socket name
    addr.sun_path[0] = '\0';  // Set first byte to null for abstract namespace
    strncpy(&addr.sun_path[1], socketName.c_str(), sizeof(addr.sun_path) - 2);
    
    // Calculate the length of the address structure
    // For abstract namespace, we need to include the null byte + socket name length
    socklen_t addrLen = offsetof(struct sockaddr_un, sun_path) + 1 + socketName.length();
    
    LOG_INFO("Attempting to bind abstract namespace socket");
    if (bind(serverFd, (struct sockaddr*)&addr, addrLen) < 0) {
        LOG_ERROR("Failed to bind abstract namespace socket: " + std::string(strerror(errno)));
        close(serverFd);
        return false;
    }
    LOG_INFO("Abstract namespace socket bound successfully");
    
    // 开始监听
    LOG_INFO("Starting to listen on socket with backlog=5");
    if (listen(serverFd, 5) < 0) {
        LOG_ERROR("Failed to listen on socket: " + std::string(strerror(errno)));
        close(serverFd);
        return false;
    }
    LOG_INFO("Socket listening started successfully");
    
    // Abstract namespace sockets don't have file permissions
    LOG_INFO("Abstract namespace socket doesn't require file permissions");
    
    running = true;
    serverThread = std::thread(&SocketServer::runServer, this);
    
    LOG_INFO("Socket server started successfully on abstract namespace: " + socketName);
    return true;
}

void SocketServer::stop() {
    LOG_INFO("Stopping socket server...");
    running = false;
    
    // 首先关闭socket文件描述符，这会导致accept()返回EBADF
    if (serverFd >= 0) {
        LOG_DEBUG("Closing server socket fd: " + std::to_string(serverFd));
        close(serverFd);
        serverFd = -1;
    }
    
    // 等待服务器线程退出
    if (serverThread.joinable()) {
        LOG_DEBUG("Waiting for server thread to join...");
        serverThread.join();
        LOG_DEBUG("Server thread joined successfully");
    }
    
    // Abstract namespace sockets don't create files, so no need to unlink
    LOG_INFO("Abstract namespace socket closed (no file to remove)");
    
    LOG_INFO("Socket server stopped");
}

void SocketServer::runServer() {
    LOG_INFO("Server thread started, waiting for client connections...");
    
    while (running) {
        LOG_DEBUG("Waiting for client connection...");
        
        // 阻塞等待连接，零CPU开销
        struct sockaddr_un clientAddr;
        socklen_t clientLen = sizeof(clientAddr);
        int clientFd = accept(serverFd, (struct sockaddr*)&clientAddr, &clientLen);
        
        if (clientFd >= 0) {
            activeClients++;
            LOG_INFO("Client connected successfully, fd: " + std::to_string(clientFd) +
                    ", active clients: " + std::to_string(activeClients));
            
            // 通知客户端连接回调
            if (clientConnectedCallback) {
                clientConnectedCallback();
            }
            
            // 处理客户端请求（保持持久连接，处理多个请求）
            handleClient(clientFd);
            
            // 客户端断开后，继续等待新的连接
            LOG_DEBUG("Client handler finished, waiting for new connection...");
            
        } else if (errno != EINTR) {
            LOG_ERROR("Accept failed: " + std::string(strerror(errno)) +
                     " (errno: " + std::to_string(errno) + ")");
            
            if (errno == EBADF) {
                LOG_ERROR("Invalid socket file descriptor, server may be stopping");
                break;
            } else if (errno == EINVAL) {
                LOG_ERROR("Socket not listening, attempting to restart listen");
                if (listen(serverFd, 5) < 0) {
                    LOG_ERROR("Failed to restart listening: " + std::string(strerror(errno)));
                    break;
                }
            }
        } else {
            LOG_DEBUG("Accept interrupted by signal, continuing...");
        }
    }
    
    LOG_INFO("Server thread exiting");
}

void SocketServer::handleClient(int clientFd) {
    LOG_INFO("Starting client handler, fd: " + std::to_string(clientFd) +
             " - will maintain persistent connection");
    
    // 持续处理多个请求，直到连接断开
    while (running) {
        try {
            // 读取请求
            std::string request = readRequest(clientFd);
            
            // 立即处理请求
            std::string response = processRequest(request);
            
            // 发送响应
            sendResponse(clientFd, response);
            
            LOG_DEBUG("Request processed successfully, waiting for next request...");
            
        } catch (const std::exception& e) {
            LOG_ERROR("Error handling client request: " + std::string(e.what()));
            // 连接断开或出错，退出循环
            break;
        }
    }
    
    // 关闭客户端连接
    close(clientFd);
    activeClients--;
    LOG_INFO("Client disconnected, fd: " + std::to_string(clientFd) +
             ", active clients: " + std::to_string(activeClients));
    
    // 通知客户端断开回调
    if (clientDisconnectedCallback) {
        clientDisconnectedCallback();
    }
}

std::string SocketServer::readRequest(int clientFd) {
    // 读取请求长度（使用网络字节序）
    int32_t length;
    if (read(clientFd, &length, sizeof(length)) != sizeof(length)) {
        throw std::runtime_error("Failed to read request length");
    }
    
    // 从网络字节序转换为主机字节序
    length = ntohl(length);
    
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
    int32_t responseLength = static_cast<int32_t>(response.length());
    
    // 转换为网络字节序
    int32_t networkLength = htonl(responseLength);
    
    if (write(clientFd, &networkLength, sizeof(networkLength)) != sizeof(networkLength)) {
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
        if (type == "set_charge_limit") {
            return processSetChargeLimit(request);
        }
        
        // 状态查询：从缓存获取
        if (type == "get_battery_status") {
            return processStatusQuery(request);
        }
        
        // 轻量级测试指令
        if (type == "ping") {
            return processPingRequest(request);
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

std::string SocketServer::processSetChargeLimit(const Json::Value& request) {
    Json::Value response;
    response["success"] = true;
    response["data"] = Json::Value(Json::objectValue);
    
    int limit = request["limit"].asInt();
    bool success = DataCollector::getInstance().setChargeLimit(limit);
    response["success"] = success;
    response["data"]["applied"] = success;
    response["data"]["timestamp"] = Json::Value::Int64(std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count());
    
    LOG_INFO("Charge limit set: " + std::to_string(limit) + 
                " (success: " + std::to_string(success) + ")");
    
    Json::StreamWriterBuilder builder;
    return Json::writeString(builder, response);
}

std::string SocketServer::processStatusQuery(const Json::Value& request) {
    Json::Value response;
    response["success"] = true;

    // 阻塞查询
    BatteryData data = DataCollector::getInstance().getCurrentData();
    
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

std::string SocketServer::processPingRequest(const Json::Value& request) {
    Json::Value response;
    response["success"] = true;
    response["data"] = Json::Value(Json::objectValue);
    response["data"]["message"] = "pong";
    response["data"]["timestamp"] = Json::Value::Int64(std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count());
    
    LOG_DEBUG("Ping request processed successfully");
    
    Json::StreamWriterBuilder builder;
    return Json::writeString(builder, response);
}