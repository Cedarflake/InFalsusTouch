#pragma once

#include <array>
#include <cstddef>
#include <cstdint>

namespace ift {

constexpr std::size_t maxControllers = 7;
constexpr std::uint8_t allControls = 127;

class ControlGroup {
public:
  void join(std::size_t device);
  void leave(std::size_t device);
  bool assign(std::size_t device, std::uint8_t requested);
  std::uint8_t controls(std::size_t device) const { return assignments_[device]; }
  std::uint8_t size() const;

private:
  std::array<std::uint8_t, maxControllers> assignments_{};
  std::array<bool, maxControllers> connected_{};
};

}
