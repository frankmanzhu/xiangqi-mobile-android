cmake_minimum_required(VERSION 3.22.1)
project(pikafish_jni CXX C)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(PIKAFISH_SRC ${CMAKE_CURRENT_SOURCE_DIR}/pikafish/src)

# Every Pikafish translation unit except the UCI `main` (we drive the engine
# through the bridge instead) and the architecture "universal" entry points.
file(GLOB_RECURSE PIKAFISH_SOURCES CONFIGURE_DEPENDS ${PIKAFISH_SRC}/*.cpp)
list(FILTER PIKAFISH_SOURCES EXCLUDE REGEX ".*/universal/.*")
list(FILTER PIKAFISH_SOURCES EXCLUDE REGEX ".*/main\\.cpp$")

add_library(pikafish_jni SHARED
    ${PIKAFISH_SOURCES}
    bridge/PikafishBridge.cpp
    jni/pikafish_jni.cpp)

target_include_directories(pikafish_jni PRIVATE ${PIKAFISH_SRC} bridge)
target_compile_definitions(pikafish_jni PRIVATE IS_64BIT NDEBUG)
target_compile_options(pikafish_jni PRIVATE -O3 -fvisibility=hidden -Wno-deprecated-declarations)

if(ANDROID_ABI STREQUAL "arm64-v8a")
    target_compile_definitions(pikafish_jni PRIVATE USE_NEON=8)
elseif(ANDROID_ABI STREQUAL "x86_64")
    target_compile_definitions(pikafish_jni PRIVATE ZSTD_DISABLE_ASM)
endif()

# 16 KB page-size compatible ELF segments (required for Android 15+ devices).
target_link_options(pikafish_jni PRIVATE -Wl,-z,max-page-size=16384)
target_link_libraries(pikafish_jni PRIVATE log)
