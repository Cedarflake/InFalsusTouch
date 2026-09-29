#pragma once

#include <cstdint>
#include <stdexcept>

namespace ift {

class FramePacer {
public:
  explicit FramePacer(std::uint16_t fps) {
    if (fps < 24 || fps > 120) throw std::invalid_argument("Unsupported video frame rate");
    interval_ = 1'000'000'000ULL / fps;
  }

  bool ready(std::uint64_t now) const { return next_ == 0 || now >= next_; }

  void submitted(std::uint64_t now) {
    // Sample the latest capture at stream cadence; never replay missed slots after a stall.
    next_ = next_ == 0 || now >= next_ + interval_ ? now + interval_ : next_ + interval_;
  }

private:
  std::uint64_t interval_ = 0;
  std::uint64_t next_ = 0;
};

}
