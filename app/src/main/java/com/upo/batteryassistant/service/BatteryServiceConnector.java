package com.upo.batteryassistant.service;

import android.util.Log;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Magic Service连接器
 * 负责与Battery Service Daemon通信
 */
public class BatteryServiceConnector {
    private static final String TAG = "BatteryServiceConnector";
    private static final String SOCKET_PATH = "/data/local/tmp/battery_service.sock";
    private static final int CONNECT_TIMEOUT = 2000; // 2秒连接超时
    
    private final ExecutorService executorService;
    private boolean isConnected = false;
    
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
     */
    public boolean connect() {
        try {
            LocalSocket socket = new LocalSocket();
            socket.connect(new LocalSocketAddress(SOCKET_PATH));
            socket.close();
            isConnected = true;
            Log.i(TAG, "Connected to battery service daemon");
            return true;
        } catch (Exception e) {
            isConnected = false;
            Log.e(TAG, "Failed to connect to battery service daemon", e);
            return false;
        }
    }
    
    /**
     * 断开连接
     */
    public void disconnect() {
        isConnected = false;
        Log.i(TAG, "Disconnected from battery service daemon");
    }
    
    /**
     * 检查连接状态
     */
    public boolean isConnected() {
        return isConnected;
    }
    
    /**
     * 获取电池状态
     */
    public CompletableFuture<BatteryData> getBatteryStatus() {
        return CompletableFuture.supplyAsync(() -> {
            try (LocalSocket socket = new LocalSocket();
                 DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                 DataInputStream input = new DataInputStream(socket.getInputStream())) {
                
                // 建立连接
                socket.connect(new LocalSocketAddress(SOCKET_PATH));
                
                // 构建状态查询请求
                JSONObject request = new JSONObject();
                request.put("type", "get_battery_status");
                
                // 发送请求
                sendRequest(socket, output, request);
                
                // 接收响应
                JSONObject response = receiveResponse(socket, input);
                
                if (response.getBoolean("success")) {
                    return BatteryData.fromJson(response.getJSONObject("data"));
                } else {
                    Log.e(TAG, "Failed to get battery status: " + response.getString("error"));
                    return null;
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error getting battery status", e);
                return null;
            }
        }, executorService);
    }
    
    /**
     * 设置充电阈值
     */
    public CompletableFuture<Boolean> setChargeThreshold(int startThreshold, int endThreshold) {
        return CompletableFuture.supplyAsync(() -> {
            try (LocalSocket socket = new LocalSocket();
                 DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                 DataInputStream input = new DataInputStream(socket.getInputStream())) {
                
                // 建立连接
                socket.connect(new LocalSocketAddress(SOCKET_PATH));
                
                // 构建控制命令
                JSONObject request = new JSONObject();
                request.put("type", "set_charge_threshold");
                
                JSONObject config = new JSONObject();
                config.put("start_threshold", startThreshold);
                config.put("end_threshold", endThreshold);
                request.put("config", config);
                
                // 发送请求
                sendRequest(socket, output, request);
                
                // 接收响应
                JSONObject response = receiveResponse(socket, input);
                
                if (response.getBoolean("success")) {
                    Log.i(TAG, "Charge threshold set successfully: " + startThreshold + "-" + endThreshold);
                    return true;
                } else {
                    Log.e(TAG, "Failed to set charge threshold: " + response.getString("error"));
                    return false;
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error setting charge threshold", e);
                return false;
            }
        }, executorService);
    }
    
    /**
     * 设置充电限制
     */
    public CompletableFuture<Boolean> setChargeLimit(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try (LocalSocket socket = new LocalSocket();
                 DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                 DataInputStream input = new DataInputStream(socket.getInputStream())) {
                
                // 建立连接
                socket.connect(new LocalSocketAddress(SOCKET_PATH));
                
                // 构建控制命令
                JSONObject request = new JSONObject();
                request.put("type", "set_charge_limit");
                request.put("limit", limit);
                
                // 发送请求
                sendRequest(socket, output, request);
                
                // 接收响应
                JSONObject response = receiveResponse(socket, input);
                
                if (response.getBoolean("success")) {
                    Log.i(TAG, "Charge limit set successfully: " + limit);
                    return true;
                } else {
                    Log.e(TAG, "Failed to set charge limit: " + response.getString("error"));
                    return false;
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error setting charge limit", e);
                return false;
            }
        }, executorService);
    }
    
    /**
     * 启用/禁用充电
     */
    public CompletableFuture<Boolean> enableCharging(boolean enable) {
        return CompletableFuture.supplyAsync(() -> {
            try (LocalSocket socket = new LocalSocket();
                 DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                 DataInputStream input = new DataInputStream(socket.getInputStream())) {
                
                // 建立连接
                socket.connect(new LocalSocketAddress(SOCKET_PATH));
                
                // 构建控制命令
                JSONObject request = new JSONObject();
                request.put("type", "enable_charging");
                request.put("enable", enable);
                
                // 发送请求
                sendRequest(socket, output, request);
                
                // 接收响应
                JSONObject response = receiveResponse(socket, input);
                
                if (response.getBoolean("success")) {
                    Log.i(TAG, "Charging " + (enable ? "enabled" : "disabled") + " successfully");
                    return true;
                } else {
                    Log.e(TAG, "Failed to " + (enable ? "enable" : "disable") + " charging: " + response.getString("error"));
                    return false;
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error " + (enable ? "enabling" : "disabling") + " charging", e);
                return false;
            }
        }, executorService);
    }
    
    /**
     * 发送请求到Magic Service
     */
    private void sendRequest(LocalSocket socket, DataOutputStream output, JSONObject request) throws IOException, JSONException {
        String requestStr = request.toString();
        byte[] requestData = requestStr.getBytes("UTF-8");
        
        output.writeInt(requestData.length);
        output.write(requestData);
        output.flush();
        
        Log.d(TAG, "Sent request: " + requestStr);
    }
    
    /**
     * 从Magic Service接收响应
     */
    private JSONObject receiveResponse(LocalSocket socket, DataInputStream input) throws IOException, JSONException {
        // 读取响应长度
        int length = input.readInt();
        
        if (length <= 0 || length > 1024 * 1024) {
            throw new IOException("Invalid response length: " + length);
        }
        
        // 读取响应数据
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
    }
    
    /**
     * 清理资源
     */
    public void cleanup() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
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