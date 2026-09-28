#pragma once

#include <fstream>
#include <string>

#include "windows/input/input-state.h"

namespace ift {

class TraceInput : public InputSink {
public:
  explicit TraceInput(const std::wstring& path);
  bool key(std::uint8_t lane, bool down) noexcept override;
  bool absolute(Point clientPoint) noexcept override;
  bool relative(int deltaX) noexcept override;

private:
  std::ofstream output_;
};

}
