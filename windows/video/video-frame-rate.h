#pragma once

#include <array>
#include <atomic>
#include <cstdint>
#include <stdexcept>

#include "windows/input/control-group.h"

namespace ift {

class VideoFrameRate {
public:
  explicit VideoFrameRate(std::uint16_t fallback) : fallback_(fallback), current_(fallback) {
    validate(fallback);
  }

  void request(std::size_t device, std::uint16_t fps) {
    validate(fps);
    requests_.at(device) = fps;
    update();
  }

  void clear(std::size_t device) {
    requests_.at(device) = 0;
    update();
  }

  std::uint16_t current() const { return current_.load(std::memory_order_relaxed); }

private:
  static void validate(std::uint16_t fps) {
    if (fps < 24 || fps > 120) throw std::invalid_argument("Video FPS must be in [24, 120]");
  }

  void update() {
    std::uint16_t requested = 0;
    for (const auto fps : requests_) {
      if (fps != 0 && (requested == 0 || fps < requested)) requested = fps;
    }
    current_.store(requested == 0 ? fallback_ : requested, std::memory_order_relaxed);
  }

  const std::uint16_t fallback_;
  // Only the control thread changes requests; the video thread reads current_.
  std::array<std::uint16_t, maxControllers> requests_{};
  std::atomic<std::uint16_t> current_;
};

}
