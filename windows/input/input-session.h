#pragma once

#include "windows/input/input-state.h"

namespace ift {

class InputSession {
public:
  explicit InputSession(InputState& state);
  Packet process(const Packet& packet, double seconds);

private:
  InputState& state_;
  std::uint32_t nextSequence_ = 1;
  bool hasHello_ = false;
};

}
