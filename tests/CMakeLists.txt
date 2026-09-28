add_executable(ift_core_tests
  test-main.cpp
  protocol-tests.cpp
  input-tests.cpp
  mapping-tests.cpp
  direct-field-tests.cpp
  video-tests.cpp
  profile-tests.cpp
  coop-tests.cpp
)
target_link_libraries(ift_core_tests PRIVATE ift_core)
target_compile_definitions(ift_core_tests PRIVATE IFT_SOURCE_DIR="${PROJECT_SOURCE_DIR}")
foreach(suite IN ITEMS protocol input mapping video profile coop)
  add_test(NAME ${suite} COMMAND ift_core_tests ${suite})
endforeach()

if(WIN32 AND IFT_BUILD_HOST)
  add_executable(ift_input_target win32-input-target.cpp)
  target_link_libraries(ift_input_target PRIVATE user32)
  target_compile_definitions(ift_input_target PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX UNICODE _UNICODE)
  add_executable(ift_video_pattern d3d-pattern.cpp)
  target_link_libraries(ift_video_pattern PRIVATE user32 d3d11 dxgi windowsapp)
  target_compile_definitions(ift_video_pattern PRIVATE WIN32_LEAN_AND_MEAN NOMINMAX UNICODE _UNICODE)
endif()
