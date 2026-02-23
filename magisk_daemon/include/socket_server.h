#ifndef SOCKET_SERVER_H
#define SOCKET_SERVER_H

#include <string>
#include <functional>
#include <atomic>
#include <thread>
#include <json/json.h>

class SocketServer {
private:
    std::string socketName; // Abstract namespace socket name (starts with null byte internally)
    int serverFd;
    std::atomic<bool> running{true};
    std::atomic<int> activeClients{0};
    std::thread serverThread;
    
    std::function<void()> clientConnectedCallback;
    std::function<void()> clientDisconnectedCallback;
    
    void runServer();
    void handleClient(int clientFd);
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