# Momo Subtitle — native build for vendored whisper.cpp v1.7.4 (commit 8a9ad78).
# CPU-only (NEON on arm64). Keep native code isolated here; JNI is the only bridge.

cmake_minimum_required(VERSION 3.22)
project(momo_whisper_jni LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper)
set(GGML_DIR ${WHISPER_DIR}/ggml)

set(GGML_SOURCES
    ${GGML_DIR}/src/ggml.c
    ${GGML_DIR}/src/ggml-alloc.c
    ${GGML_DIR}/src/ggml-backend.cpp
    ${GGML_DIR}/src/ggml-backend-reg.cpp
    ${GGML_DIR}/src/ggml-quants.c
    ${GGML_DIR}/src/ggml-threading.cpp
    ${GGML_DIR}/src/ggml-opt.cpp
)

set(GGML_CPU_SOURCES
    ${GGML_DIR}/src/ggml-cpu/ggml-cpu.c
    ${GGML_DIR}/src/ggml-cpu/ggml-cpu.cpp
    ${GGML_DIR}/src/ggml-cpu/ggml-cpu-aarch64.cpp
    ${GGML_DIR}/src/ggml-cpu/ggml-cpu-quants.c
    ${GGML_DIR}/src/ggml-cpu/ggml-cpu-traits.cpp
)

# Backend registry needs GGML_USE_CPU to register the CPU backend (mirrors
# whisper.cpp's own CMake). GGML_USE_CPU_AARCH64 enables the aarch64 kernels.
add_library(ggml STATIC ${GGML_SOURCES})
target_include_directories(ggml PUBLIC ${GGML_DIR}/include PRIVATE ${GGML_DIR}/src)
target_compile_definitions(ggml PUBLIC GGML_USE_CPU GGML_SCHED_MAX_COPIES=4)

add_library(ggml-cpu STATIC ${GGML_CPU_SOURCES})
target_include_directories(ggml-cpu
    PUBLIC ${GGML_DIR}/include
    PRIVATE ${GGML_DIR}/src ${GGML_DIR}/src/ggml-cpu)
target_compile_definitions(ggml-cpu PRIVATE GGML_USE_CPU GGML_USE_CPU_AARCH64)

add_library(whisper STATIC ${WHISPER_DIR}/src/whisper.cpp)
target_include_directories(whisper
    PUBLIC ${WHISPER_DIR}/include
    PRIVATE ${GGML_DIR}/include ${GGML_DIR}/src)

add_library(momo_whisper_jni SHARED jni/momo_jni.c)
target_include_directories(momo_whisper_jni
    PRIVATE ${WHISPER_DIR}/include ${GGML_DIR}/include)
target_link_libraries(momo_whisper_jni PRIVATE whisper ggml ggml-cpu log)
