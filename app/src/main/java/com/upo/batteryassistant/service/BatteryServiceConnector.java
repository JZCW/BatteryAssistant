package com.upo.batteryassistant.service;

import android.util.Log;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.LocalServerSocket;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Magic Service连接器
 * 负责与Battery Service Daemon通信
 * 
 * 架构说明:
 * - App创建abstract namespace socket服务器
 * - Proxy以root权限连接到App的socket
 * - Proxy再连接到Daemon的abstract namespace socket
 * - Proxy在App和Daemon之间转发数据
 */
public class BatteryServiceConnector {
    private static final String TAG = "BatteryServiceConnector";
    private static final String APP_SOCKET_NAME = "battery_service_app"; // App的abstract namespace socket名称
    private static final String PROXY_EXECUTABLE = "/data/adb/modules/batteryAssistant/batteryProxy"; // Proxy可执行文件路径
    private static final String DAEMON_SOCKET_NAME = "battery_service"; // Daemon的abstract namespace socket名称
    private static final int CONNECT_TIMEOUT = 2000; // 2秒连接超时
    
    private final ExecutorService executorService;
    private Process proxyProcess = null;
    private LocalServerSocket serverSocket = null;
    private final AtomicBoolean serverRunning = new AtomicBoolean(false);
    private Thread serverThread = null;
    private LocalSocket proxySocket = null; // 保存Proxy的连接
    private DataInputStream proxyInput = null; // Proxy连接的输入流
    private DataOutputStream proxyOutput = null; // Proxy连接的输出流
    private final Object proxyLock = new Object(); // 用于同步Proxy连接访问
    private volatile boolean connectionHealthy = false; // 连接健康状态标记
    
    public interface BatteryDataListener {
        void onBatteryDataChanged(BatteryData data);
    }
    
    private BatteryDataListener listener;
    
    public BatteryServiceConnector() {
        this.executorService = Executors.newFixedThreadPool(3);
    }
    
    public void setBatteryDataListener(BatteryDataListener listener) {
        this.listener = listener;
    }
    
    /**
     * 连接到Magic Service
     * socket服务器和proxy只启动一次
     */
    public synchronized boolean connect() {
        // 首先检查当前连接状态，如果不健康则清理
        if (!isConnectionHealthy()) {
            Log.d(TAG, "Connection is not healthy, cleaning up before reconnect");
            cleanupProxyConnection();
            resetSocketServer();
        }
        
        // 启动本地socket服务器
        int serverStatus = startSocketServer();
        if (serverStatus == 1) {
            Log.e(TAG, "Failed to start local socket server");
            stopSocketServer();
            return false;
        }
        
        // 启动代理客户端
        int proxyStatus = startProxy();
        if (proxyStatus == 1) {
            Log.e(TAG, "Failed to start proxy client");
            stopProxy();
            return false;
        }

        if (serverStatus == 2 && proxyStatus == 2 && isConnectionHealthy()) {
            Log.d(TAG, "Already connected and healthy");
            return true;
        }

        // 测试连接
        if (testConnection()) {
            connectionHealthy = true;
            return true;
        }
        
        return false;
    }
    
    /**
     * 检查连接是否健康
     * @return true如果连接健康，false否则
     */
    private boolean isConnectionHealthy() {
        // 检查proxy进程是否存活
        if (!isProxyRunning()) {
            Log.d(TAG, "Proxy process is not running");
            return false;
        }
        
        // 检查socket连接是否可用
        synchronized (proxyLock) {
            if (proxySocket == null || proxyInput == null || proxyOutput == null) {
                Log.d(TAG, "Proxy socket or streams are null");
                return false;
            }
            
            // 简单的socket状态检查
            try {
                // 尝试检查socket是否已关闭，处理UnsupportedOperationException
                try {
                    if (proxySocket.isClosed()) {
                        Log.d(TAG, "Proxy socket is closed");
                        return false;
                    }
                } catch (UnsupportedOperationException e) {
                    // 某些Android版本不支持isClosed()方法，跳过此检查
                    Log.d(TAG, "isClosed() not supported, skipping socket closed check");
                }
                return connectionHealthy;
            } catch (Exception e) {
                Log.d(TAG, "Error checking connection health", e);
                return false;
            }
        }
    }
    
    /**
     * 清理proxy连接
     */
    private void cleanupProxyConnection() {
        Log.d(TAG, "Cleaning up proxy connection...");
        connectionHealthy = false;
        
        synchronized (proxyLock) {
            if (proxyOutput != null) {
                try {
                    proxyOutput.close();
                } catch (IOException e) {
                    Log.w(TAG, "Error closing proxy output stream during cleanup", e);
                }
                proxyOutput = null;
            }
            if (proxyInput != null) {
                try {
                    proxyInput.close();
                } catch (IOException e) {
                    Log.w(TAG, "Error closing proxy input stream during cleanup", e);
                }
                proxyInput = null;
            }
            if (proxySocket != null) {
                try {
                    proxySocket.close();
                } catch (IOException e) {
                    Log.w(TAG, "Error closing proxy socket during cleanup", e);
                }
                proxySocket = null;
            }
        }
        
        Log.i(TAG, "Proxy connection cleaned up");
    }
    
    /**
     * 重置socket服务器状态
     */
    private void resetSocketServer() {
        Log.d(TAG, "Resetting socket server state...");
        
        // 如果服务器正在运行但状态不一致，先停止
        if (serverRunning.get() && (serverSocket == null || serverThread == null)) {
            Log.w(TAG, "Server running but components are null, stopping server");
            stopSocketServer();
        }
        
        Log.i(TAG, "Socket server state reset");
    }
    
    /**
     * 启动本地abstract namespace socket服务器
     * @return 0: 成功, 1: 失败, 2: 已经运行
     */
    private int startSocketServer() {
        if (!serverRunning.get()) {
            Log.d(TAG, "Socket server is not running, starting it...");
        } else {
            Log.d(TAG, "Socket server is already running, skipping start");
            return 2;
        }
        
        Log.d(TAG, "Starting local abstract namespace socket server: " + APP_SOCKET_NAME);
        
        try {
            // 创建abstract namespace socket服务器
            serverSocket = new LocalServerSocket(APP_SOCKET_NAME);
            serverRunning.set(true);
            
            // 启动服务器线程接受连接
            serverThread = new Thread(this::runServer);
            serverThread.setName("BatteryServiceConnector-Server");
            serverThread.start();
            
            Log.i(TAG, "Local socket server started successfully");
            return 0;
            
        } catch (IOException e) {
            Log.e(TAG, "Failed to start local socket server", e);
            return 1;
        }
    }
    
    /**
     * 运行socket服务器，接受代理连接
     * 只接受第一个连接（Proxy连接），之后不再接受新连接
     */
    private void runServer() {
        Log.d(TAG, "Server thread started, waiting for proxy connection...");
        
        // 只接受第一个连接（Proxy连接）
        while (serverRunning.get()) {
            try {
                // 等待代理连接
                LocalSocket clientSocket = serverSocket.accept();
                Log.i(TAG, "Proxy connected to local socket server");
                
                // 处理代理连接
                handleProxyConnection(clientSocket);
                
                // Proxy连接已建立，退出accept循环
                break;
                
            } catch (IOException e) {
                if (serverRunning.get()) {
                    Log.e(TAG, "Error accepting connection", e);
                }
                break;
            }
        }
        
        Log.d(TAG, "Server thread exiting");
    }
    
    /**
     * 处理代理连接
     */
    private void handleProxyConnection(LocalSocket clientSocket) {
        synchronized (proxyLock) {
            try {
                // 保存Proxy连接的socket和流
                proxySocket = clientSocket;
                proxyInput = new DataInputStream(clientSocket.getInputStream());
                proxyOutput = new DataOutputStream(clientSocket.getOutputStream());
                
                // 连接建立后设置为健康状态
                connectionHealthy = true;
                Log.i(TAG, "Proxy connection established and saved, connection marked as healthy");
                
            } catch (IOException e) {
                Log.e(TAG, "Error setting up proxy connection", e);
                connectionHealthy = false;
                try {
                    clientSocket.close();
                } catch (IOException ex) {
                    Log.w(TAG, "Error closing client socket", ex);
                }
            }
        }
    }
    
    /**
     * 停止本地socket服务器
     */
    private void stopSocketServer() {
        Log.d(TAG, "Stopping local socket server...");
        serverRunning.set(false);
        
        // 使用专门的清理方法清理Proxy连接
        cleanupProxyConnection();
        
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                Log.w(TAG, "Error closing server socket", e);
            }
            serverSocket = null;
        }
        
        if (serverThread != null) {
            try {
                serverThread.join(CONNECT_TIMEOUT);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            serverThread = null;
        }
        
        Log.i(TAG, "Local socket server stopped");
    }
    
    /**
     * 检查代理是否正在运行
     * @return true如果代理正在运行，false否则
     */
    public boolean isProxyRunning() {
        return proxyProcess != null && proxyProcess.isAlive();
    }

    /**
     * 启动代理客户端
     * @return 0: 成功, 1: 失败, 2: 已经运行
     */
    private int startProxy() {
        if (!isProxyRunning()) {
            Log.d(TAG, "Proxy is not running, starting it...");
        } else {
            Log.d(TAG, "Proxy is already running, skipping start");
            return 2;
        }
        
        try {
            // 使用root权限启动代理客户端
            // 代理连接到App的abstract namespace socket和Daemon的abstract namespace socket
            String[] cmd = {
                "su",
                "-c",
                PROXY_EXECUTABLE + " " + APP_SOCKET_NAME + " " + DAEMON_SOCKET_NAME
            };
            
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            proxyProcess = pb.start();
            
            // 读取代理输出（用于调试）
            new Thread(() -> {
                try {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(proxyProcess.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Log.i(TAG, "Proxy: " + line);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error reading proxy output", e);
                }
            }).start();
            
            // 读取错误输出
            new Thread(() -> {
                try {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(proxyProcess.getErrorStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Log.e(TAG, "Proxy error: " + line);
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error reading proxy error output", e);
                }
            }).start();
            
            // 等待代理进程启动并检查是否存活
            // try {
            //     Thread.sleep(500);
            // } catch (InterruptedException e) {
            //     Thread.currentThread().interrupt();
            //     return false;
            // }
            
            // 检查进程是否存活
            if (!proxyProcess.isAlive()) {
                Log.e(TAG, "Proxy process died immediately");
                return 1;
            }
            
            Log.i(TAG, "Proxy client started successfully");
            return 0;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start proxy client", e);
            return 1;
        }
    }
    
    /**
     * 停止代理客户端
     */
    private void stopProxy() {
        if (proxyProcess != null) {
            Log.d(TAG, "Stopping proxy client...");
            proxyProcess.destroy();
            try {
                proxyProcess.waitFor(2000, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            proxyProcess = null;
            Log.i(TAG, "Proxy client stopped");
        }
    }
    
    /**
     * 测试连接
     */
    private boolean testConnection() {
        try {
            // 发送一个测试请求
            CompletableFuture<BatteryData> testResult = getBatteryStatus();
            BatteryData result = testResult.get(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS);
            
            if (result != null) {
                Log.i(TAG, "Connection test passed");
                return true;
            } else {
                Log.w(TAG, "Connection test failed: null result");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Connection test failed", e);
            return false;
        }
    }
    
    /**
     * 测试连接并返回详细诊断信息
     */
    public String testConnectionWithDiagnostics() {
        StringBuilder diagnostics = new StringBuilder();
        diagnostics.append("=== Socket Connection Diagnostics ===\n");
        
        // 1. 检查本地socket服务器状态
        diagnostics.append("Local socket name: ").append(APP_SOCKET_NAME).append("\n");
        diagnostics.append("Server running: ").append(serverRunning.get()).append("\n");
        diagnostics.append("Server socket: ").append(serverSocket != null ? "created" : "null").append("\n");
        diagnostics.append("Proxy process: ").append(proxyProcess != null ? "running" : "null").append("\n");
        diagnostics.append("Connection healthy: ").append(connectionHealthy).append("\n");
        
        // 2. 检查Proxy连接状态
        synchronized (proxyLock) {
            diagnostics.append("Proxy socket: ").append(proxySocket != null ? "connected" : "null").append("\n");
            if (proxySocket != null) {
                try {
                    diagnostics.append("Proxy socket closed: ").append(proxySocket.isClosed()).append("\n");
                } catch (UnsupportedOperationException e) {
                    diagnostics.append("Proxy socket closed: unknown (UnsupportedOperationException)\n");
                } catch (Exception e) {
                    diagnostics.append("Proxy socket closed: error (").append(e.getClass().getSimpleName()).append(")\n");
                }
            }
            diagnostics.append("Proxy input stream: ").append(proxyInput != null ? "available" : "null").append("\n");
            diagnostics.append("Proxy output stream: ").append(proxyOutput != null ? "available" : "null").append("\n");
        }
        
        // 3. 通过实际请求测试连接
        diagnostics.append("\n=== Connection Test ===\n");
        try {
            // 发送一个测试请求
            CompletableFuture<BatteryData> testResult = getBatteryStatus();
            BatteryData result = testResult.get(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS);
            
            if (result != null) {
                diagnostics.append("Connection test PASSED\n");
                diagnostics.append("Battery data received: ").append(result.toString()).append("\n");
            } else {
                diagnostics.append("Connection test FAILED: null result\n");
            }
        } catch (Exception e) {
            diagnostics.append("Connection test FAILED: ").append(e.getClass().getSimpleName()).append("\n");
            diagnostics.append("Error message: ").append(e.getMessage()).append("\n");
            
            if (e.getCause() != null) {
                diagnostics.append("Root cause: ").append(e.getCause().getMessage()).append("\n");
            }
        }
        
        String result = diagnostics.toString();
        Log.d(TAG, "Diagnostics result:\n" + result);
        return result;
    }
    
    /**
     * 断开连接
     */
    public void disconnect() {
        Log.d(TAG, "Disconnecting from battery service daemon...");
        
        // 停止代理客户端
        stopProxy();
        // 停止本地socket服务器
        stopSocketServer();
        
        // 确保连接状态被重置
        connectionHealthy = false;
        
        Log.i(TAG, "Disconnected from battery service daemon");
    }
    
    /**
     * 发送请求到代理
     * 使用已建立的Proxy连接，而不是创建新连接
     */
    private CompletableFuture<JSONObject> sendRequestToProxy(JSONObject request) {
        return CompletableFuture.supplyAsync(() -> {
            DataInputStream input = null;
            DataOutputStream output = null;
            
            synchronized (proxyLock) {
                // 使用已保存的Proxy连接
                if (proxyInput == null || proxyOutput == null) {
                    Log.e(TAG, "Proxy connection not available");
                    connectionHealthy = false;
                    return null;
                }
                input = proxyInput;
                output = proxyOutput;
            }
            
            try {
                // 发送请求
                String requestStr = request.toString();
                byte[] requestData = requestStr.getBytes("UTF-8");
                
                output.writeInt(requestData.length);
                output.write(requestData);
                output.flush();
                
                Log.d(TAG, "Sent request: " + requestStr);
                
                // 接收响应
                int length = input.readInt();
                
                if (length <= 0 || length > 1024 * 1024) {
                    throw new IOException("Invalid response length: " + length);
                }
                
                byte[] responseData = new byte[length];
                int bytesRead = 0;
                while (bytesRead < length) {
                    int n = input.read(responseData, bytesRead, length - bytesRead);
                    if (n <= 0) {
                        throw new IOException("Connection closed while reading response");
                    }
                    bytesRead += n;
                }
                
                String responseStr = new String(responseData, "UTF-8");
                JSONObject response = new JSONObject(responseStr);
                
                Log.d(TAG, "Received response: " + responseStr);
                return response;
                
            } catch (IOException e) {
                Log.e(TAG, "Connection error while sending request to proxy", e);
                // 连接断开，清理连接状态
                connectionHealthy = false;
                // 异步清理连接，避免在同步块中执行耗时操作
                CompletableFuture.runAsync(this::cleanupProxyConnection, executorService);
                return null;
            } catch (Exception e) {
                Log.e(TAG, "Error sending request to proxy", e);
                return null;
            }
        }, executorService);
    }
    
    /**
     * 获取电池状态
     */
    public CompletableFuture<BatteryData> getBatteryStatus() {
        try {
            JSONObject request = new JSONObject();
            request.put("type", "get_battery_status");
            return sendRequestToProxy(request)
                .thenApply(response -> {
                    if (response != null && response.optBoolean("success")) {
                        try {
                            return BatteryData.fromJson(response.getJSONObject("data"));
                        } catch (JSONException e) {
                            Log.e(TAG, "Failed to parse battery data", e);
                            return null;
                        }
                    } else {
                        Log.e(TAG, "Failed to get battery status: " +
                            (response != null ? response.optString("error", "unknown") : "null response"));
                        return null;
                    }
                });
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create request", e);
            return CompletableFuture.completedFuture(null);
        }
    }
    
    /**
     * 设置充电限制
     */
    public CompletableFuture<Boolean> setChargeLimit(int limit) {
        try {
            JSONObject request = new JSONObject();
            request.put("type", "set_charge_limit");
            request.put("limit", limit);
            
            return sendRequestToProxy(request)
                .thenApply(response -> {
                    if (response != null && response.optBoolean("success")) {
                        Log.i(TAG, "Charge limit set successfully: " + limit);
                        return true;
                    } else {
                        Log.e(TAG, "Failed to set charge limit: " +
                            (response != null ? response.optString("error", "unknown") : "null response"));
                        return false;
                    }
                });
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create request", e);
            return CompletableFuture.completedFuture(false);
        }
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        disconnect();
    }
}
