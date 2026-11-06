cmake_minimum_required(VERSION 3.22.1)

project(prostovoice_native LANGUAGES CXX)

add_library(prostovoice_native SHARED prostovoice_native.cpp)

target_compile_features(prostovoice_native PRIVATE cxx_std_17)

find_library(log-lib log)
target_link_libraries(prostovoice_native PRIVATE ${log-lib})
