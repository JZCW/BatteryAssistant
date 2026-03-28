package com.upo.batteryassistant.service;

import android.util.Log;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.net.LocalServerSocket;
import com.upo.batteryassistant.data.BatteryData;
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
     * 确保 bridge 进程已启动（用于服务保活守护）
     * 只启动 socket server 和 proxy，不等待连接测试
     * 如果 proxy 可执行文件不存在（无 root 环境），静默跳过
     */
    public synchronized void ensureBridgeStarted() {
        // 检查 proxy 可执行文件是否存在（需要 root 权限才能访问该路径）
        try {
            Process checkProcess = Runtime.getRuntime().exec(new String[]{"su", "-c", "test -f " + PROXY_EXECUTABLE});
            boolean finished = checkProcess.waitFor(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS);
            if (!finished || checkProcess.exitValue() != 0) {
                Log.d(TAG, "Proxy executable not found or no root, skipping bridge start");
                return;
            }
        } catch (Exception e) {
            Log.d(TAG, "Root check failed, skipping bridge start: " + e.getMessage());
            return;
        }

        // 启动 socket server
        int serverStatus = startSocketServer();
        if (serverStatus == 1) {
            Log.e(TAG, "ensureBridgeStarted: failed to start socket server");
            stopSocketServer();
            return;
        }

        // 启动 proxy
        int proxyStatus = startProxy();
        if (proxyStatus == 1) {
            Log.e(TAG, "ensureBridgeStarted: failed to start proxy");
            stopProxy();
            return;
        }

        if (serverStatus == 2 && proxyStatus == 2) {
            Log.d(TAG, "ensureBridgeStarted: bridge already running");
        } else {
            Log.i(TAG, "ensureBridgeStarted: bridge started successfully");
        }
    }

    /**
     * 连接到Magic Service
     * socket服务器和proxy只启动一次
     */
    public synchronized boolean connect() {
        // 首先检查当前连接状态，如果不健康则清理
        if (!isConnectionHealthy()) {
            Log.d(TAG, "Connection is not healthy, cleaning up before reconnect");
            // 强制重启socket服务器来重新创建accept线程
            stopSocketServer();
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

        if (serverStatus == 2 && proxyStatus == 2) {
            Log.d(TAG, "Already connected");
            return true;
        }

        // 测试连接
        return testConnection();
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

            return true;
        }
    }
    
    /**
     * 清理proxy连接
     */
    private void cleanupProxyConnection() {
        Log.d(TAG, "Cleaning up proxy connection...");
        
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
                Log.i(TAG, "Proxy connection established and saved, connection marked as healthy");
                
            } catch (IOException e) {
                Log.e(TAG, "Error setting up proxy connection", e);
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
                proxyProcess.waitFor(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            proxyProcess = null;
            Log.i(TAG, "Proxy client stopped");
        }
    }
    
    /**
     * 轻量级连接测试
     */
    private CompletableFuture<Boolean> ping() {
        try {
            JSONObject request = new JSONObject();
            request.put("type", "ping");
            return sendRequestToProxy(request)
                .thenApply(response -> {
                    if (response != null && response.optBoolean("success")) {
                        Log.d(TAG, "Ping test passed");
                        return true;
                    } else {
                        Log.w(TAG, "Ping test failed: " +
                            (response != null ? response.optString("error", "unknown") : "null response"));
                        return false;
                    }
                });
        } catch (JSONException e) {
            Log.e(TAG, "Failed to create ping request", e);
            return CompletableFuture.completedFuture(false);
        }
    }
    
    /**
     * 测试连接
     */
    public boolean testConnection() {
        try {
            // 发送轻量级测试请求
            CompletableFuture<Boolean> testResult = ping();
            Boolean result = testResult.get(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS);
            
            if (result != null && result) {
                Log.i(TAG, "Connection test passed");
                return true;
            } else {
                Log.w(TAG, "Connection test failed: ping failed");
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
            CompletableFuture<Boolean> testResult = ping();
            Boolean result = testResult.get(CONNECT_TIMEOUT, TimeUnit.MILLISECONDS);
            if (result != null) {
                diagnostics.append("Connection test PASSED\n");
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
    private void disconnect() {
        Log.d(TAG, "Disconnecting from battery service daemon...");
        
        // 停止代理客户端
        stopProxy();
        // 停止本地socket服务器
        stopSocketServer();
        
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
                        String errorMsg = "Connection closed while reading response";
                        if (n == 0) {
                            errorMsg = "Connection closed by peer while reading response";
                        }
                        throw new IOException(errorMsg);
                    }
                    bytesRead += n;
                }
                
                String responseStr = new String(responseData, "UTF-8");
                JSONObject response = new JSONObject(responseStr);
                
                Log.d(TAG, "Received response: " + responseStr);
                return response;
                
            } catch (IOException e) {
                String errorMessage = e.getMessage();
                if (errorMessage != null && errorMessage.contains("Broken pipe")) {
                    Log.e(TAG, "Proxy connection broken (proxy likely died), marking connection as unhealthy", e);
                    // Broken pipe表示连接断开，清理连接状态
                    // 异步清理连接，避免在同步块中执行耗时操作
                    CompletableFuture.runAsync(this::cleanupProxyConnection, executorService);
                } else {
                    Log.e(TAG, "Connection error while sending request to proxy", e);
                    // 其他IO错误也可能表示连接问题，也清理连接状态
                    CompletableFuture.runAsync(this::cleanupProxyConnection, executorService);
                }
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
