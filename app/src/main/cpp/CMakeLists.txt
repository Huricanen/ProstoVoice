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
set(SHERPA_ONNX_ENABLE_PYTHON OFF CACHE BOOL "" FORCE)
set(SHERPA_ONNX_ENABLE_TESTS OFF CACHE BOOL "" FORCE)
set(SHERPA_ONNX_ENABLE_CHECK OFF CACHE BOOL "" FORCE)
set(SHERPA_ONNX_ENABLE_PORTAUDIO OFF CACHE BOOL "" FORCE)
set(SHERPA_ONNX_ENABLE_WEBSOCKET OFF CACHE BOOL "" FORCE)
set(SHERPA_ONNX_ENABLE_TTS OFF CACHE BOOL "" FORCE)
set(SHERPA_ONNX_ENABLE_BINARY OFF CACHE BOOL "" FORCE)
set(SHERPA_ONNX_ENABLE_SPEAKER_DIARIZATION ON CACHE BOOL "" FORCE)
set(SHERPA_ONNX_ENABLE_C_API ON CACHE BOOL "" FORCE)
set(SHERPA_ONNX_ENABLE_JNI OFF CACHE BOOL "" FORCE)
set(SHERPA_ONNX_USE_PRE_INSTALLED_ONNXRUNTIME_IF_AVAILABLE OFF CACHE BOOL "" FORCE)
add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/whisper.cpp whisper-build EXCLUDE_FROM_ALL)
add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/sherpa-onnx sherpa-onnx-build EXCLUDE_FROM_ALL)

add_library(prostovoice_native SHARED prostovoice_native.cpp)

target_compile_features(prostovoice_native PRIVATE cxx_std_17)
target_include_directories(prostovoice_native PRIVATE
    ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/whisper.cpp/include
    ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/sherpa-onnx/sherpa-onnx/c-api)

find_library(log-lib log)
target_link_libraries(prostovoice_native PRIVATE whisper sherpa-onnx-c-api ${log-lib})
