package com.upo.batteryassistant.util;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

/**
 * Root 权限工具类
 */
public class RootUtil {

  private RootUtil() {
  }

  /**
   * 检测是否可以使用 su 获取 root 权限
   */
  public static boolean isRootAvailable() {
    Process process = null;
    DataOutputStream os = null;
    try {
      process = Runtime.getRuntime().exec("su");
      os = new DataOutputStream(process.getOutputStream());
      os.writeBytes("exit\n");
      os.flush();
      int exitValue = process.waitFor();
      return exitValue == 0;
    } catch (Exception e) {
      return false;
    } finally {
      if (os != null) {
        try {
          os.close();
        } catch (Exception ignored) {
        }
      }
      if (process != null) {
        process.destroy();
      }
    }
  }

  /**
   * 使用 su 执行命令，返回标准输出
   */
  public static String executeCommand(String command) {
    Process process = null;
    DataOutputStream os = null;
    BufferedReader reader = null;
    try {
      process = Runtime.getRuntime().exec("su");
      os = new DataOutputStream(process.getOutputStream());
      os.writeBytes(command + "\n");
      os.writeBytes("exit\n");
      os.flush();

      StringBuilder output = new StringBuilder();
      reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
      String line;
      while ((line = reader.readLine()) != null) {
        output.append(line).append("\n");
      }

      process.waitFor();
      return output.toString();
    } catch (Exception e) {
      return null;
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (Exception ignored) {
        }
      }
      if (os != null) {
        try {
          os.close();
        } catch (Exception ignored) {
        }
      }
      if (process != null) {
        process.destroy();
      }
    }
  }

  /**
   * 读取文件内容（通过 su + cat）
   */
  public static String readFile(String filePath) {
    String cmd = "cat " + filePath;
    String result = executeCommand(cmd);
    if (result == null) {
      return null;
    }
    // 只取第一行，去除尾部换行
    int idx = result.indexOf('\n');
    if (idx >= 0) {
      return result.substring(0, idx).trim();
    }
    return result.trim();
  }

  /**
   * 判断文件是否存在（通过 su 执行 test -e）
   */
  public static boolean fileExists(String filePath) {
    String cmd = "[ -e \"" + filePath + "\" ] && echo 1 || echo 0";
    String result = executeCommand(cmd);
    if (result == null) {
      return false;
    }
    result = result.trim();
    return "1".equals(result);
  }
}


