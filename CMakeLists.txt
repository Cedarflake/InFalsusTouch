cmake_minimum_required(VERSION 3.24)
project(InFalsusTouch VERSION 0.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
if(MSVC)
  set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
  add_compile_options(/W4 /WX /permissive- /utf-8 /EHsc)
else()
  add_compile_options(-Wall -Wextra -Wpedantic -Werror)
endif()

add_library(ift_core
  protocol/cpp/packet.cpp
  protocol/cpp/video-packet.cpp
  protocol/cpp/h264.cpp
  windows/input/field-mapper.cpp
  windows/input/direct-field.cpp
  windows/input/input-state.cpp
  windows/input/input-session.cpp
  windows/input/input-mixer.cpp
  windows/input/control-group.cpp
  windows/config/host-profile.cpp
  windows/config/key-bindings.cpp
)
target_include_directories(ift_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

option(IFT_BUILD_HOST "Build the Windows host" ${WIN32})
include(CTest)
if(BUILD_TESTING)
  add_subdirectory(tests)
endif()
if(IFT_BUILD_HOST AND WIN32)
  add_subdirectory(windows)
endif()
