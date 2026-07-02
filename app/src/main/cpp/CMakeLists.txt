cmake_minimum_required(VERSION 3.22.1)
project(whisper-jni C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# whisper.cpp source is vendored here by scripts/fetch-models.sh (gitignored). It uses the flat
# ggml layout of whisper.cpp v1.5.x: ggml.c / ggml-alloc.c / ggml-backend.c / ggml-quants.c.
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper)

add_library(
    whisper-jni SHARED
    whisper-jni.c
    ${WHISPER_DIR}/ggml.c
    ${WHISPER_DIR}/ggml-alloc.c
    ${WHISPER_DIR}/ggml-backend.c
    ${WHISPER_DIR}/ggml-quants.c
    ${WHISPER_DIR}/whisper.cpp
)

target_include_directories(whisper-jni PRIVATE ${WHISPER_DIR})

# arm64-v8a has NEON as baseline, so no special -march is needed (keeping the default keeps the
# binary runnable on older armv8.0 chips). -O3/-ffast-math speed up inference.
target_compile_options(whisper-jni PRIVATE -O3 -ffast-math -fno-finite-math-only -pthread)

find_library(log-lib log)
target_link_libraries(whisper-jni ${log-lib})
