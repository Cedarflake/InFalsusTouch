add_executable(InFalsusTouchHost
  main.cpp
  config/host-options.cpp
  config/profile-file.cpp
  config/field-calibration.cpp
  config/game-bindings.cpp
  input/win32-input.cpp
  input/trace-input.cpp
  target/game-window.cpp
  transport/control-server.cpp
  transport/video-server.cpp
  video/media-runtime.cpp
  capture/window-capture.cpp
  capture/frame-converter.cpp
  encoder/encoder-config.cpp
  encoder/hardware-encoder.cpp
)
target_link_libraries(InFalsusTouchHost PRIVATE ift_core ws2_32 user32
  windowsapp d3d11 dxgi mfplat mf mfuuid ole32 dxguid dwmapi shell32)
target_compile_definitions(InFalsusTouchHost PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX UNICODE _UNICODE)
if(MSVC)
  target_link_options(InFalsusTouchHost PRIVATE /MANIFEST:EMBED
    /MANIFESTINPUT:${CMAKE_CURRENT_SOURCE_DIR}/host.manifest)
endif()
