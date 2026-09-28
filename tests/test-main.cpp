#include "tests/test-support.h"

#include <iostream>

int main(int argc, char** argv) {
  try {
    const std::string suite = argc > 1 ? argv[1] : "all";
    if (suite == "protocol" || suite == "all") {
      protocolTests();
    }
    if (suite == "input" || suite == "all") {
      inputTests();
    }
    if (suite == "mapping" || suite == "all") {
      mappingTests();
    }
    std::cout << suite << " tests passed\n";
    return 0;
  } catch (const std::exception& error) {
    std::cerr << error.what() << '\n';
    return 1;
  }
}
