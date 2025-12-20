cmake_minimum_required(VERSION 3.18.1)
project("battery_service_daemon")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -O2")

# 设置包含目录
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

# 查找依赖库
find_package(PkgConfig REQUIRED)

# 源文件
set(SOURCES
    src/main.cpp
    src/data_collector.cpp
    src/cache_manager.cpp
    src/socket_server.cpp
    src/charge_controller.cpp
    src/logger.cpp
)

# 创建可执行文件
add_executable(battery_service_daemon ${SOURCES})

# 链接库
target_link_libraries(battery_service_daemon 
    pthread
    dl
)

# 编译选项
target_compile_options(battery_service_daemon PRIVATE -pthread)

# 安装规则
install(TARGETS battery_service_daemon 
    DESTINATION ${CMAKE_INSTALL_PREFIX}/bin
)

# 设置编译器特定的选项
if(CMAKE_CXX_COMPILER_ID STREQUAL "GNU" OR CMAKE_CXX_COMPILER_ID STREQUAL "Clang")
    target_compile_options(battery_service_daemon PRIVATE -Wno-unused-parameter)
endif()

# 添加自定义目标用于清理
add_custom_target(distclean
    COMMAND ${CMAKE_COMMAND} -E remove_directory ${CMAKE_BINARY_DIR}/CMakeFiles
    COMMAND ${CMAKE_COMMAND} -E remove ${CMAKE_BINARY_DIR}/CMakeCache.txt
    COMMAND ${CMAKE_COMMAND} -E remove ${CMAKE_BINARY_DIR}/cmake_install.cmake
    COMMAND ${CMAKE_COMMAND} -E remove ${CMAKE_BINARY_DIR}/Makefile
    COMMAND ${CMAKE_COMMAND} -E remove ${CMAKE_BINARY_DIR}/battery_service_daemon
)