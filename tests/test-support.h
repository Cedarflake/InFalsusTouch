#pragma once

#include <functional>
#include <stdexcept>
#include <string>
#include <vector>

#include "windows/input/input-session.h"

inline void check(bool condition, const char* message) {
  if (!condition) {
    throw std::runtime_error(message);
  }
}

template <typename Exception, typename Operation>
void expectError(Operation operation) {
  try {
    operation();
  } catch (const Exception&) {
    return;
  }
  throw std::runtime_error("Expected exception was not thrown");
}

struct RecordingSink : ift::InputSink {
  std::vector<int> keys;
  ift::Point point;
  int relativeX = 0;
  bool fail = false;

  bool key(std::uint8_t lane, bool down) noexcept override {
    keys.push_back(down ? lane : -static_cast<int>(lane));
    return !fail;
  }
  bool absolute(ift::Point value) noexcept override {
    point = value;
    return !fail;
  }
  bool relative(int value) noexcept override {
    relativeX += value;
    return !fail;
  }
};

void protocolTests();
void inputTests();
void mappingTests();
