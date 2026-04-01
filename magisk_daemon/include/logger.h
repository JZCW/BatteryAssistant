#ifndef LOGGER_H
#define LOGGER_H

#include <string>
#include <fstream>
#include <mutex>
#include <chrono>
#include <iomanip>
#include <sstream>
#include <filesystem>

class Logger {
public:
    enum Level { DEBUG, INFO, WARN, ERROR };

private:
    static std::ofstream logFile;
    static std::mutex logMutex;
    static bool initialized;
    static Level currentLevel;
    static const size_t MAX_LOG_SIZE = 10 * 1024 * 1024; // 10MB
    static std::string logPath;

public:
    static void init(const std::string& logPath);
    static void setLevel(Level level);
    static void log(Level level, const std::string& message);
    static void debug(const std::string& message) { log(DEBUG, message); }
    static void info(const std::string& message) { log(INFO, message); }
    static void warn(const std::string& message) { log(WARN, message); }
    static void error(const std::string& message) { log(ERROR, message); }

private:
    static std::string getLevelString(Level level);
    static std::string getCurrentTimestamp();
    // timestamp 由 log() 统一计算后传入，确保控制台与文件记录同一时刻
    static void writeToFile(Level level, const std::string& message, const std::string& timestamp);
    static void rotateLogFile();
};

// 便捷宏定义
#define LOG_DEBUG(msg) Logger::debug(msg)
#define LOG_INFO(msg) Logger::info(msg)
#define LOG_WARN(msg) Logger::warn(msg)
#define LOG_ERROR(msg) Logger::error(msg)

#endif // LOGGER_H