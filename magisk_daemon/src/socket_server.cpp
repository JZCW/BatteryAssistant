#include "socket_server.h"
#include "data_collector.h"
#include "logger.h"
#include "socket_utils.h"
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/stat.h>
#include <unistd.h>
#include <fcntl.h>
#include <cstring>
#include <iostream>
#include <arpa/inet.h>
#include <chrono>
#include <thread>

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
    
    struct sockaddr_un addr;
    socklen_t addrLen = 0;
    if (!buildAbstractSockaddr(socketName, addr, addrLen)) {
        const size_t maxAbstractNameLen = sizeof(addr.sun_path) - 1;
        LOG_ERROR("Abstract socket name too long: " + std::to_string(socketName.size()) +
                  " bytes (max " + std::to_string(maxAbstractNameLen) + ")");
        close(serverFd);
        serverFd = -1;
        return false;
    }
    
    LOG_INFO("Attempting to bind abstract namespace socket");
    if (bind(serverFd, (struct sockaddr*)&addr, addrLen) < 0) {
        LOG_ERROR("Failed to bind abstract namespace socket: " + std::string(strerror(errno)));
        close(serverFd);
        // Keep fd state consistent for stop()/destructor paths.
        serverFd = -1;
        return false;
    }
    LOG_INFO("Abstract namespace socket bound successfully");
    
    // 开始监听
    // backlog=1：单客户端设计（注释见类头文件），仅允许一个待连接在内核队列中排队，
    LOG_INFO("Starting to listen on socket with backlog=1");
    if (listen(serverFd, 1) < 0) {
        LOG_ERROR("Failed to listen on socket: " + std::string(strerror(errno)));
        close(serverFd);
        // Keep fd state consistent for stop()/destructor paths.
        serverFd = -1;
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

    // 关闭活跃客户端连接，打断 handleClient 中阻塞 read
    closeActiveClientForStop();
    
    // 并发场景下仅 close() 不保证立刻打断其他线程中的阻塞 accept()。
    // 先 shutdown() 再 close()，确保监听套接字上的阻塞系统调用被唤醒。
    if (serverFd >= 0) {
        LOG_DEBUG("Closing server socket fd: " + std::to_string(serverFd));
        shutdown(serverFd, SHUT_RDWR);
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

void SocketServer::closeActiveClientForStop() {
    std::lock_guard<std::mutex> lock(clientFdMutex);
    if (currentClientFd >= 0) {
        shutdown(currentClientFd, SHUT_RDWR);
        close(currentClientFd);
        currentClientFd = -1;
    }
}

void SocketServer::runServer() {
    LOG_INFO("Server thread started, waiting for client connections...");
    int transientAcceptFailures = 0;
    constexpr int MAX_ACCEPT_FAILURES_BEFORE_BACKOFF = 5;
    constexpr int ACCEPT_BACKOFF_MS = 200;
    
    while (running) {
        LOG_DEBUG("Waiting for client connection...");
        
        // 阻塞等待连接，零CPU开销
        struct sockaddr_un clientAddr;
        socklen_t clientLen = sizeof(clientAddr);
        int clientFd = accept(serverFd, (struct sockaddr*)&clientAddr, &clientLen);
        
        if (clientFd >= 0) {
            transientAcceptFailures = 0;
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
                // EINVAL 表示 socket 已处于不可恢复状态，重新 listen 无意义且行为未定义
                LOG_ERROR("Socket is in an invalid state (EINVAL), stopping server");
                break;
            } else {
                transientAcceptFailures++;
                // 非致命错误连续出现时退避，避免日志风暴与 CPU 空转。
                if (transientAcceptFailures >= MAX_ACCEPT_FAILURES_BEFORE_BACKOFF) {
                    LOG_WARN("Accept keeps failing, applying backoff: " +
                             std::to_string(ACCEPT_BACKOFF_MS) + "ms");
                    std::this_thread::sleep_for(std::chrono::milliseconds(ACCEPT_BACKOFF_MS));
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

    {
        std::lock_guard<std::mutex> lock(clientFdMutex);
        currentClientFd = clientFd;
    }
    
    // 持续处理多个请求，直到连接断开
    // 分层处理：readRequest/sendResponse 是连接层，失败则断开；
    // processRequest 内部已捕获所有异常并返回错误 JSON，不影响连接
    while (running) {
        std::string request;
        try {
            request = readRequest(clientFd);
        } catch (const std::exception& e) {
            LOG_ERROR("Connection error reading request: " + std::string(e.what()));
            break;
        }

        // processRequest 内部捕获所有异常，此处不会抛出
        std::string response = processRequest(request);

        try {
            sendResponse(clientFd, response);
        } catch (const std::exception& e) {
            LOG_ERROR("Connection error sending response: " + std::string(e.what()));
            break;
        }

        LOG_DEBUG("Request processed successfully, waiting for next request...");
    }
    
    // 在互斥锁内检查后再决定是否关闭 fd：
    // closeActiveClientForStop() 会在持锁状态下关闭 fd 并将 currentClientFd 清零。
    // 若此处 currentClientFd 仍等于 clientFd，说明 stop() 尚未处理，由本函数负责关闭；
    // 若已被清零，则跳过 close，防止 fd 被内核复用后误关无关文件描述符。
    {
        std::lock_guard<std::mutex> lock(clientFdMutex);
        if (currentClientFd == clientFd) {
            close(clientFd);
            currentClientFd = -1;
        }
        // else: stop() 已通过 closeActiveClientForStop() 关闭并清零，无需重复 close
    }
    activeClients--;
    LOG_INFO("Client disconnected, fd: " + std::to_string(clientFd) +
             ", active clients: " + std::to_string(activeClients));
    
    // 通知客户端断开回调
    if (clientDisconnectedCallback) {
        try {
            // 回调异常不应影响 server 连接生命周期。
            clientDisconnectedCallback();
        } catch (const std::exception& e) {
            LOG_ERROR("Client disconnected callback exception: " + std::string(e.what()));
        } catch (...) {
            LOG_ERROR("Client disconnected callback exception: unknown error");
        }
    }
}

std::string SocketServer::readRequest(int clientFd) {
    // 读取请求长度（使用网络字节序）
    // 流式套接字下单次 read 可能不足 4 字节，必须循环读满
    int32_t length;
    {
        ssize_t totalRead = 0;
        while (totalRead < static_cast<ssize_t>(sizeof(length))) {
            ssize_t n = read(clientFd,
                            reinterpret_cast<char*>(&length) + totalRead,
                            sizeof(length) - static_cast<size_t>(totalRead));
            if (n < 0) {
                // 被信号中断时重试，避免误判断连
                if (errno == EINTR) continue;
                throw std::runtime_error("Failed to read request length");
            }
            if (n == 0) {
                throw std::runtime_error("Connection closed by peer while reading request length");
            }
            totalRead += n;
        }
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
        ssize_t n = read(clientFd, &requestData[bytesRead], static_cast<size_t>(length - bytesRead));
        if (n < 0) {
            // 被信号中断时重试，避免误判断连
            if (errno == EINTR) continue;
            throw std::runtime_error("Failed to read request data");
        }
        if (n == 0) {
            throw std::runtime_error("Connection closed by peer while reading request data");
        }
        bytesRead += static_cast<int>(n);
    }
    
    return requestData;
}

void SocketServer::sendResponse(int clientFd, const std::string& response) {
    int32_t responseLength = static_cast<int32_t>(response.length());
    
    // 转换为网络字节序
    int32_t networkLength = htonl(responseLength);
    
    // 流式套接字下 write 可能部分写入，循环写出直至完整发送
    {
        ssize_t totalWritten = 0;
        while (totalWritten < static_cast<ssize_t>(sizeof(networkLength))) {
            ssize_t n = write(clientFd,
                             reinterpret_cast<const char*>(&networkLength) + totalWritten,
                             sizeof(networkLength) - static_cast<size_t>(totalWritten));
            if (n < 0) {
                // 被信号中断时重试，避免误判发送失败
                if (errno == EINTR) continue;
                throw std::runtime_error("Failed to write response length");
            }
            if (n == 0) {
                throw std::runtime_error("Failed to write response length");
            }
            totalWritten += n;
        }
    }
    {
        ssize_t totalWritten = 0;
        while (totalWritten < static_cast<ssize_t>(responseLength)) {
            ssize_t n = write(clientFd,
                             response.c_str() + totalWritten,
                             static_cast<size_t>(responseLength) - static_cast<size_t>(totalWritten));
            if (n < 0) {
                // 被信号中断时重试，避免误判发送失败
                if (errno == EINTR) continue;
                throw std::runtime_error("Failed to write response data");
            }
            if (n == 0) {
                throw std::runtime_error("Failed to write response data");
            }
            totalWritten += n;
        }
    }
}

std::string SocketServer::processRequest(const std::string& requestData) {
    try {
        // 解析JSON请求
        Json::Value request;
        Json::CharReaderBuilder readerBuilder;
        std::unique_ptr<Json::CharReader> reader(readerBuilder.newCharReader());
        std::string parseErrs;
        if (!reader->parse(requestData.c_str(), requestData.c_str() + requestData.size(), &request, &parseErrs)) {
            throw std::runtime_error("Invalid JSON request: " + parseErrs);
        }

        // 明确校验 type 字段，避免缺失时返回空字符串造成定位困难。
        if (!request.isMember("type") || !request["type"].isString()) {
            throw std::runtime_error("Invalid request: missing or non-string 'type'");
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
    Json::Value::Int64 nowMs = static_cast<Json::Value::Int64>(
        std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::system_clock::now().time_since_epoch()).count());

    Json::Value response;
    response["success"] = true;
    response["data"] = Json::Value(Json::objectValue);
    
    // Validate that "limit" key exists and is an integer
    if (!request.isMember("limit") || !request["limit"].isInt()) {
        response["success"] = false;
        response["error"] = "invalid_request: missing or invalid 'limit' field (must be integer)";
        response["data"]["applied"] = false;
        response["data"]["timestamp"] = nowMs;
    } else {
        int limit = request["limit"].asInt();
        // Input contract: only non-negative values are accepted.
        if (limit < 0) {
            response["success"] = false;
            response["error"] = "invalid_limit: must be non-negative";
            response["data"]["applied"] = false;
            response["data"]["timestamp"] = nowMs;
        } else {
            bool success = DataCollector::getInstance().setChargeLimit(limit);
            response["success"] = success;
            response["data"]["applied"] = success;
            response["data"]["timestamp"] = nowMs;

            LOG_INFO("Charge limit set: " + std::to_string(limit) +
                        " (success: " + std::to_string(success) + ")");
        }
    }
    
    Json::StreamWriterBuilder builder;
    return Json::writeString(builder, response);
}

std::string SocketServer::processStatusQuery(const Json::Value& request) {
    Json::Value response;
    response["success"] = true;

    // 阻塞查询
    BatteryData data = DataCollector::getInstance().getCurrentData();
    
    response["data"] = data.toJson();
    
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