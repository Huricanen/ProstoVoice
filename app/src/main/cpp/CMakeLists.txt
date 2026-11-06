cmake_minimum_required(VERSION 3.22.1)

project(prostovoice_native LANGUAGES CXX)

# Keep the debug APK debuggable while compiling the heavy Whisper/ggml code
# with release optimizations. Unoptimized C++ makes on-device inference
# impractically slow on ARM phones.
set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)

set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(GGML_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
# Android builds run on a different CPU from the host performing the Gradle
# build; native host detection emits invalid flags such as -mcpu=native.
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/whisper.cpp whisper-build EXCLUDE_FROM_ALL)

add_library(prostovoice_native SHARED prostovoice_native.cpp)

target_compile_features(prostovoice_native PRIVATE cxx_std_17)
target_include_directories(prostovoice_native PRIVATE ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/whisper.cpp/include)

find_library(log-lib log)
target_link_libraries(prostovoice_native PRIVATE whisper ${log-lib})
