#include "logger.h"
#include <iostream>

std::ofstream Logger::logFile;
std::mutex Logger::logMutex;
bool Logger::initialized = false;
Logger::Level Logger::currentLevel = Logger::INFO;
std::string Logger::logPath;

void Logger::init(const std::string& path) {
    std::lock_guard<std::mutex> lock(logMutex);
    if (!initialized) {
        logPath = path;
        logFile.open(logPath, std::ios::app);
        initialized = true;
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
    
    std::string timestamp = getCurrentTimestamp();
    std::string levelStr = getLevelString(level);
    
    // 输出到控制台
    std::cout << "[" << timestamp << "] [" << levelStr << "] " << message << std::endl;
    
    // 写入日志文件
    writeToFile(level, message);
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

void Logger::writeToFile(Level level, const std::string& message) {
    if (!initialized) return;
    
    std::lock_guard<std::mutex> lock(logMutex);
    if (logFile.is_open()) {
        // 检查文件大小，如果超过限制则轮转
        rotateLogFile();
        
        logFile << "[" << getCurrentTimestamp() << "] [" 
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
        
        // 将当前日志文件备份为 .bak
        std::string backupPath = logPath + ".bak";
        
        // 删除旧的备份文件
        if (std::filesystem::exists(backupPath)) {
            std::filesystem::remove(backupPath);
        }
        
        // 重命名当前日志文件为备份
        std::filesystem::rename(logPath, backupPath);
        
        // 重新打开新日志文件
        logFile.open(logPath, std::ios::app);
    }
}