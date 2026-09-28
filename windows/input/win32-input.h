#pragma once

#include "windows/input/input-state.h"

namespace ift {

class Win32Input : public InputSink {
public:
  bool key(std::uint8_t lane, bool down) noexcept override;
  bool absolute(Point clientPoint) noexcept override;
  bool relative(int deltaX) noexcept override;
  void setBindings(const KeyBindings& bindings) override { bindings_ = bindings; }

private:
  KeyBindings bindings_ = defaultBindings;
  std::array<bool, 6> pressed_{};
};

}
