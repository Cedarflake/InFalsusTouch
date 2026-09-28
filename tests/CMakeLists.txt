add_executable(ift_core_tests
  test-main.cpp
  protocol-tests.cpp
  input-tests.cpp
  mapping-tests.cpp
)
target_link_libraries(ift_core_tests PRIVATE ift_core)
target_compile_definitions(ift_core_tests PRIVATE IFT_SOURCE_DIR="${PROJECT_SOURCE_DIR}")
foreach(suite IN ITEMS protocol input mapping)
  add_test(NAME ${suite} COMMAND ift_core_tests ${suite})
endforeach()
