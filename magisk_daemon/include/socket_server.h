#ifndef SOCKET_SERVER_H
#define SOCKET_SERVER_H

#include <string>
#include <functional>
#include <atomic>
#include <thread>
#include <mutex>
#include <json/json.h>

/**
 * 单客户端设计：同一时刻仅支持一个持久连接。
 * runServer() 在当前连接断开前不会 accept 下一个，新连接在内核 backlog 中排队等待。
 * 当前架构中唯一客户端为 bridge（PID 文件锁保证单实例），无需并发支持。
 */
class SocketServer {
private:
    std::string socketName; // Abstract namespace socket name (starts with null byte internally)
    int serverFd;
    std::atomic<bool> running{false};
    std::atomic<int> activeClients{0};
    std::thread serverThread;
    int currentClientFd{-1};
    std::mutex clientFdMutex;
    
    std::function<void()> clientConnectedCallback;
    std::function<void()> clientDisconnectedCallback;
    
    void runServer();
    void handleClient(int clientFd);
    void closeActiveClientForStop();
    std::string readRequest(int clientFd);
    void sendResponse(int clientFd, const std::string& response);
    std::string processRequest(const std::string& requestData);
    std::string processSetChargeLimit(const Json::Value& request);
    std::string processStatusQuery(const Json::Value& request);
    std::string processOtherRequest(const Json::Value& request);
    std::string processPingRequest(const Json::Value& request);
    
public:
    SocketServer(const std::string& socketName); // socketName: abstract namespace socket name (not a file path)
    ~SocketServer();
    
    bool start();
    void stop();
    
    void setClientConnectedCallback(std::function<void()> callback) {
        clientConnectedCallback = callback;
    }
    
    void setClientDisconnectedCallback(std::function<void()> callback) {
        clientDisconnectedCallback = callback;
    }
    
    int getActiveClients() const { return activeClients; }
    bool isRunning() const { return running; }
};

#endif // SOCKET_SERVER_H