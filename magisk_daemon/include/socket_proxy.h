#ifndef SOCKET_PROXY_H
#define SOCKET_PROXY_H

#include <string>
#include <thread>
#include <atomic>
#include <vector>
#include <ctime>
#include <sys/socket.h>
#include <sys/un.h>

/**
 * Socket代理类
 * 
 * 架构说明:
 * - App创建abstract namespace socket服务器
 * - Proxy以root权限连接到App的abstract namespace socket
 * - Proxy再连接到Daemon的abstract namespace socket
 * - Proxy在App和Daemon之间转发数据
 * 
 * 当App与Proxy的连接断开时，Proxy退出
 */
class SocketProxy {
private:
    std::string appSocketName;      // App的abstract namespace socket名称
    std::string daemonSocketName;   // Daemon的abstract namespace socket名称
    int appSocketFd;                 // App socket文件描述符
    int daemonSocketFd;              // Daemon socket文件描述符
    std::atomic<bool> running;      // 运行状态
    std::atomic<time_t> lastAppActivityTime; // 最后一次收到 app 数据的时间
    std::thread proxyThread;         // 代理线程
    
    void runProxy();
    bool connectToDaemon();          // 连接到Daemon
    bool connectToApp();              // 连接到App
    bool readMessage(int fd, std::vector<char>& buffer);  // 读取完整消息（4字节长度+数据）
    bool writeMessage(int fd, const std::vector<char>& buffer);  // 写入完整消息（4字节长度+数据）
    
public:
    SocketProxy(const std::string& appSocketName, const std::string& daemonSocketName);
    ~SocketProxy();
    
    bool start();                    // 启动代理
    void stop();                     // 停止代理
    bool isRunning() const { return running; }
    time_t getLastAppActivityTime() const { return lastAppActivityTime.load(); }
};

#endif // SOCKET_PROXY_H
