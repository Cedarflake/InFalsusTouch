#include "windows/input/control-group.h"

#include <stdexcept>

namespace ift {

std::uint8_t ControlGroup::size() const {
  std::uint8_t count = 0;
  for (const bool connected : connected_) if (connected) ++count;
  return count;
}

void ControlGroup::join(std::size_t device) {
  if (device >= maxControllers || connected_[device]) throw std::logic_error("Invalid controller slot");
  assignments_[device] = allControls;
  connected_[device] = true;
}

void ControlGroup::leave(std::size_t device) {
  assignments_[device] = 0;
  connected_[device] = false;
}

bool ControlGroup::assign(std::size_t device, std::uint8_t requested) {
  if (!connected_[device] || requested > allControls) return false;
  assignments_[device] = requested;
  return true;
}

}
