#include "logger.h"
#include <iostream>

std::ofstream Logger::logFile;
std::mutex Logger::logMutex;
bool Logger::initialized = false;

void Logger::init(const std::string& logPath) {
    std::lock_guard<std::mutex> lock(logMutex);
    if (!initialized) {
        logFile.open(logPath, std::ios::app);
        initialized = true;
        info("Logger initialized: " + logPath);
    }
}

void Logger::log(Level level, const std::string& message) {
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
        logFile << "[" << getCurrentTimestamp() << "] [" 
                 << getLevelString(level) << "] " << message << std::endl;
        logFile.flush();
    }
}