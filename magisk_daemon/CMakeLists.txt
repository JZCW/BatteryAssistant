cmake_minimum_required(VERSION 3.18.1)
project("batteryAssistant")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -O2")

# 设置包含目录
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

# 包含 FetchContent 模块
include(FetchContent)

# 获取 jsoncpp
FetchContent_Declare(
    jsoncpp
    GIT_REPOSITORY https://github.com/open-source-parsers/jsoncpp.git
    GIT_TAG        1.9.6  # 使用稳定版本
)

# 对 Android 进行特殊配置
set(JSONCPP_WITH_TESTS OFF CACHE BOOL "Build tests" FORCE)
set(JSONCPP_WITH_POST_BUILD_UNITTEST OFF CACHE BOOL "Build post-build unit-test" FORCE)
set(JSONCPP_WITH_CMAKE_PACKAGE OFF CACHE BOOL "Create CMake package config" FORCE)
set(JSONCPP_WITH_STRICT_INTEGER OFF CACHE BOOL "Use strict integer" FORCE)
set(JSONCPP_WITH_CUSTOM_UTF8_TO_STRING OFF CACHE BOOL "Use custom UTF-8 to string conversion" FORCE)
set(JSONCPP_WITH_PKGCONFIG_SUPPORT OFF CACHE BOOL "Generate pkg-config file" FORCE)

FetchContent_MakeAvailable(jsoncpp)

# 查找依赖库
find_package(PkgConfig REQUIRED)

# 设置 jsoncpp 变量
set(JSONCPP_INCLUDE_DIRS ${jsoncpp_SOURCE_DIR}/include)
set(JSONCPP_LIBRARIES jsoncpp_static)

# 源文件 (主daemon)
set(SOURCES
    src/main.cpp
    src/data_collector.cpp
    src/cache_manager.cpp
    src/socket_server.cpp
    src/charge_controller.cpp
    src/logger.cpp
)

# 调试版本源文件
set(DEBUG_SOURCES
    src/main_debug.cpp
    src/data_collector.cpp
    src/cache_manager.cpp
    src/socket_server.cpp
    src/charge_controller.cpp
    src/logger.cpp
)

# Socket代理源文件
set(PROXY_SOURCES
    src/bridge.cpp
    src/socket_proxy.cpp
    src/logger.cpp
)

# 创建可执行文件
add_executable(batteryAssistant ${SOURCES})

# 创建调试版本可执行文件
add_executable(batteryAssistant_debug ${DEBUG_SOURCES})

# 创建socket代理可执行文件
add_executable(batteryProxy ${PROXY_SOURCES})

# 链接库 - Android 中 pthread 是自动链接的
target_link_libraries(batteryAssistant
    dl
    ${JSONCPP_LIBRARIES}
)
target_include_directories(batteryAssistant PRIVATE ${JSONCPP_INCLUDE_DIRS})
target_compile_options(batteryAssistant PRIVATE ${JSONCPP_CFLAGS_OTHER})

# 链接调试版本的库
target_link_libraries(batteryAssistant_debug
    dl
    ${JSONCPP_LIBRARIES}
)
target_include_directories(batteryAssistant_debug PRIVATE ${JSONCPP_INCLUDE_DIRS})
target_compile_options(batteryAssistant_debug PRIVATE ${JSONCPP_CFLAGS_OTHER})

# 链接socket代理的库
target_link_libraries(batteryProxy dl)
target_include_directories(batteryProxy PRIVATE ${JSONCPP_INCLUDE_DIRS})

# 安装规则
install(TARGETS batteryAssistant
    DESTINATION ${CMAKE_INSTALL_PREFIX}/bin
)
install(TARGETS batteryAssistant_debug
    DESTINATION ${CMAKE_INSTALL_PREFIX}/bin
)
install(TARGETS batteryProxy
    DESTINATION ${CMAKE_INSTALL_PREFIX}/bin
)

# 设置编译器特定的选项
if(CMAKE_CXX_COMPILER_ID STREQUAL "GNU" OR CMAKE_CXX_COMPILER_ID STREQUAL "Clang")
    target_compile_options(batteryAssistant PRIVATE -Wno-unused-parameter)
    target_compile_options(batteryAssistant_debug PRIVATE -Wno-unused-parameter)
endif()

# 添加自定义目标用于清理
add_custom_target(distclean
    COMMAND ${CMAKE_COMMAND} -E remove_directory ${CMAKE_BINARY_DIR}/CMakeFiles
    COMMAND ${CMAKE_COMMAND} -E remove ${CMAKE_BINARY_DIR}/CMakeCache.txt
    COMMAND ${CMAKE_COMMAND} -E remove ${CMAKE_BINARY_DIR}/cmake_install.cmake
    COMMAND ${CMAKE_COMMAND} -E remove ${CMAKE_BINARY_DIR}/Makefile
    COMMAND ${CMAKE_COMMAND} -E remove ${CMAKE_BINARY_DIR}/batteryAssistant
    COMMAND ${CMAKE_COMMAND} -E remove ${CMAKE_BINARY_DIR}/batteryAssistant_debug
)
