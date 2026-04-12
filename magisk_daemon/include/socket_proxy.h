#ifndef SOCKET_PROXY_H
#define SOCKET_PROXY_H

#include <string>
#include <thread>
#include <atomic>
#include <mutex>
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
 * 
 * Daemon重连策略:
 * - 首次连接在后台线程中异步进行，不阻塞proxy启动
 * - Daemon断连后立即向App返回错误响应，在后台线程中异步重连
 * - 后续重连前先主动 connect 探测 daemon 是否可达，避免依赖 /proc/net/unix 文本格式
 */
class SocketProxy {
private:
    std::string appSocketName;           // App的abstract namespace socket名称
    std::string daemonSocketName;        // Daemon的abstract namespace socket名称
    std::string daemonBinaryPath;        // Daemon可执行文件路径（用于存在性检查）
    int appSocketFd;                     // App socket文件描述符（由mutex保护）
    std::mutex appFdMutex;               // 保护appSocketFd的读写
    int daemonSocketFd;                  // Daemon socket文件描述符（由mutex保护）
    std::mutex daemonFdMutex;            // 保护daemonSocketFd的读写
    std::atomic<bool> running;           // 运行状态
    std::atomic<bool> daemonConnecting;  // 后台重连是否进行中（防重入）
    std::atomic<bool> daemonUnavailable; // 永久放弃重连（二进制缺失或多次启动失败）
    // 注意：以下两个成员为非原子类型，但均只在 reconnectThread 中访问。
    // daemonConnecting 的 CAS 操作保证同一时刻只有一个重连线程存在，
    // 因此无需额外的同步原语。若未来引入多个重连线程，必须先改为原子类型或加锁保护。
    int daemonStartAttempts;             // 已尝试启动daemon的次数（仅 reconnectThread 访问）
    bool isFirstConnectAttempt;          // 首次连接标记，首次跳过预检查（仅 reconnectThread 访问）
    std::atomic<time_t> lastAppActivityTime; // 最后一次收到 app 数据的时间
    std::thread proxyThread;             // 代理线程
    std::thread reconnectThread;         // 后台重连线程
    int wakeupPipe[2];                   // 自唤醒管道：重连完成后通知proxy线程

    void runProxy();
    void reconnectLoop();                // 后台重连线程函数
    void startBackgroundReconnect();     // 安全启动后台重连（防止重复）
    bool isDaemonSocketReachable();      // 主动 connect 探测 daemon 是否可达
    bool sendErrorToApp(const std::string& errorCode); // 向App发送JSON错误响应
    bool connectToDaemon();              // 连接到Daemon（返回新fd，线程安全）
    bool connectToApp();                 // 连接到App
    bool readMessage(int fd, std::vector<char>& buffer);  // 读取完整消息（4字节长度+数据）
    bool writeMessage(int fd, const std::vector<char>& buffer);  // 写入完整消息（4字节长度+数据）

public:
    SocketProxy(const std::string& appSocketName,
                const std::string& daemonSocketName,
                const std::string& daemonBinaryPath = "");
    ~SocketProxy();

    bool start();                    // 启动代理
    void stop();                     // 停止代理
    bool isRunning() const { return running; }
    time_t getLastAppActivityTime() const { return lastAppActivityTime.load(); }
};

#endif // SOCKET_PROXY_H
