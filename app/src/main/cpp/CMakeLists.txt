cmake_minimum_required(VERSION 3.18)
project(sf_whisper C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Build whisper.cpp + ggml (CPU only) from the vendored sources
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

set(GGML_BUILD_NUMBER 1)
set(GGML_CCACHE OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_COMMIT "whisper.cpp-v1.7.6")
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_CPU ON CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)

if (ANDROID AND ANDROID_ABI STREQUAL "arm64-v8a")
    # Modern Samsung phones (2019+) support dot-product and fp16; the app checks the CPU before loading.
    set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
endif()

add_subdirectory(${WHISPER_DIR}/ggml ${CMAKE_CURRENT_BINARY_DIR}/ggml)

add_library(sf_whisper SHARED
    whisper_jni.cpp
    ${WHISPER_DIR}/src/whisper.cpp)

target_include_directories(sf_whisper PRIVATE
    ${WHISPER_DIR}/include
    ${WHISPER_DIR}/src
    ${WHISPER_DIR}/ggml/include)

target_compile_definitions(sf_whisper PRIVATE GGML_USE_CPU)

# Native code is always optimized, even in the debug APK (otherwise transcription is 10x slower)
target_compile_options(sf_whisper PRIVATE -O3)
target_compile_options(ggml-base PRIVATE -O3)
target_compile_options(ggml-cpu PRIVATE -O3)

if (ANDROID)
    find_library(LOG_LIB log)
    target_link_libraries(sf_whisper PRIVATE ggml ${LOG_LIB} android)
else()
    target_link_libraries(sf_whisper PRIVATE ggml)
endif()
