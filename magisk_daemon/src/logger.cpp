#include "logger.h"
#include <iostream>
#include <system_error>

std::ofstream Logger::logFile;
std::mutex Logger::logMutex;
bool Logger::initialized = false;
Logger::Level Logger::currentLevel = Logger::INFO;
std::string Logger::logPath;

// 语义说明：init() 为幂等调用，仅第一次成功打开的路径生效。
// 打开失败时 initialized 保持 false，允许外部再次调用 init() 重试（路径可不同）。
// 一旦成功初始化，后续所有 init() 调用均为无操作，即使传入不同路径也不会切换目标文件。
void Logger::init(const std::string& path) {
    std::lock_guard<std::mutex> lock(logMutex);
    if (!initialized) {
        logPath = path;
        logFile.open(logPath, std::ios::app);
        // Only mark initialized after successful open; allow future retry otherwise.
        if (logFile.is_open()) {
            initialized = true;
        } else {
            std::cerr << "[Logger] Failed to open log file: " << logPath << std::endl;
        }
    }
}

void Logger::setLevel(Level level) {
    currentLevel = level;
}

void Logger::log(Level level, const std::string& message) {
    // 日志级别过滤
    if (level < currentLevel) {
        return;
    }
    
    // 统一计算一次时间戳，确保控制台与文件中的时间戳完全一致
    std::string timestamp = getCurrentTimestamp();
    std::string levelStr = getLevelString(level);
    
    // 输出到控制台
    std::cout << "[" << timestamp << "] [" << levelStr << "] " << message << std::endl;
    
    // 写入日志文件，传入同一个 timestamp
    writeToFile(level, message, timestamp);
}

std::string Logger::getLevelString(Level level) {
    switch (level) {
        case DEBUG: return "DEBUG";
        case INFO:  return "INFO";
        case WARN:  return "WARN";
        case ERROR: return "ERROR";
        default: return "UNKNOWN";
    }
}

std::string Logger::getCurrentTimestamp() {
    auto now = std::chrono::system_clock::now();
    auto time_t = std::chrono::system_clock::to_time_t(now);
    auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        now.time_since_epoch()) % 1000;
    
    std::stringstream ss;
    ss << std::put_time(std::localtime(&time_t), "%Y-%m-%d %H:%M:%S");
    ss << '.' << std::setfill('0') << std::setw(3) << ms.count();
    
    return ss.str();
}

void Logger::writeToFile(Level level, const std::string& message, const std::string& timestamp) {
    if (!initialized) return;
    
    std::lock_guard<std::mutex> lock(logMutex);
    if (logFile.is_open()) {
        // 检查文件大小，如果超过限制则轮转
        rotateLogFile();
        
        // 使用上层传入的 timestamp，而非重新调用 getCurrentTimestamp()
        logFile << "[" << timestamp << "] [" 
                 << getLevelString(level) << "] " << message << std::endl;
        logFile.flush();
    }
}

void Logger::rotateLogFile() {
    if (!logFile.is_open()) return;
    
    // 获取当前文件位置（作为文件大小的近似值）
    logFile.seekp(0, std::ios::end);
    std::streampos fileSize = logFile.tellp();
    
    if (fileSize >= static_cast<std::streampos>(MAX_LOG_SIZE)) {
        logFile.close();
        // 使用 error_code 版本避免异常路径导致的编译/运行差异
        std::error_code ec;
        std::string backupPath = logPath + ".bak";

        if (std::filesystem::exists(backupPath, ec)) {
            ec.clear();
            std::filesystem::remove(backupPath, ec);
            if (ec) {
                std::cerr << "[Logger] Failed to remove old backup: " << ec.message() << std::endl;
                ec.clear();
            }
        } else if (ec) {
            std::cerr << "[Logger] Failed to check backup exists: " << ec.message() << std::endl;
            ec.clear();
        }

        std::filesystem::rename(logPath, backupPath, ec);
        if (ec) {
            std::cerr << "[Logger] Log rotation failed: " << ec.message() << std::endl;
            ec.clear();
        }

        logFile.open(logPath, std::ios::app);
        if (!logFile.is_open()) {
            std::cerr << "[Logger] Failed to open rotated log file: " << logPath << std::endl;
        }
    }
}