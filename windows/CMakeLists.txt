add_executable(InFalsusTouchHost
  main.cpp
  config/host-options.cpp
  input/win32-input.cpp
  input/trace-input.cpp
  target/game-window.cpp
  transport/control-server.cpp
)
target_link_libraries(InFalsusTouchHost PRIVATE ift_core ws2_32 user32)
target_compile_definitions(InFalsusTouchHost PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX UNICODE _UNICODE)
if(MSVC)
  target_link_options(InFalsusTouchHost PRIVATE /MANIFEST:EMBED
    /MANIFESTINPUT:${CMAKE_CURRENT_SOURCE_DIR}/host.manifest)
endif()
