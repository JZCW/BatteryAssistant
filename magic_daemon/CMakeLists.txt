cmake_minimum_required(VERSION 3.18.1)
project("battery_service_daemon")

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
if(ANDROID)
    set(JSONCPP_WITH_TESTS OFF CACHE BOOL "Build tests" FORCE)
    set(JSONCPP_WITH_POST_BUILD_UNITTEST OFF CACHE BOOL "Build post-build unit-test" FORCE)
    set(JSONCPP_WITH_CMAKE_PACKAGE OFF CACHE BOOL "Create CMake package config" FORCE)
    set(JSONCPP_WITH_STRICT_INTEGER OFF CACHE BOOL "Use strict integer" FORCE)
    set(JSONCPP_WITH_CUSTOM_UTF8_TO_STRING OFF CACHE BOOL "Use custom UTF-8 to string conversion" FORCE)
    set(JSONCPP_WITH_PKGCONFIG_SUPPORT OFF CACHE BOOL "Generate pkg-config file" FORCE)
    
    # Android 16+ FLEXIBLE_PAGE_SIZES 支持
    if(ANDROID_NATIVE_API_LEVEL GREATER_EQUAL 36)
        # 为 Android 16+ 启用灵活页面大小支持
        add_compile_options(-Wno-error=unused-command-line-argument)
        set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,--no-warn-mismatch")
        
        # 设置内存页面大小配置
        target_compile_definitions(battery_service_daemon PRIVATE
            ANDROID_API_LEVEL=${ANDROID_NATIVE_API_LEVEL}
        )
    endif()
endif()

FetchContent_MakeAvailable(jsoncpp)

# 查找依赖库
find_package(PkgConfig REQUIRED)

# 设置 jsoncpp 变量
set(JSONCPP_INCLUDE_DIRS ${jsoncpp_SOURCE_DIR}/include)
set(JSONCPP_LIBRARIES jsoncpp_static)

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

# 链接库 - Android 中 pthread 是自动链接的
if(ANDROID)
    target_link_libraries(battery_service_daemon
        dl
        ${JSONCPP_LIBRARIES}
    )
else()
    target_link_libraries(battery_service_daemon
        pthread
        dl
        ${JSONCPP_LIBRARIES}
    )
    target_compile_options(battery_service_daemon PRIVATE -pthread)
endif()
target_include_directories(battery_service_daemon PRIVATE ${JSONCPP_INCLUDE_DIRS})
target_compile_options(battery_service_daemon PRIVATE ${JSONCPP_CFLAGS_OTHER})

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