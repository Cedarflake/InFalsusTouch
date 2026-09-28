#pragma once

#include <chrono>
#include <cstdint>
#include <optional>

namespace ift {

struct FieldFeedback {
  std::uintptr_t identity = 0;
  double position = 0;
  double sensitivity = 0;
};

class DirectField {
public:
  using Clock = std::chrono::steady_clock;
  void begin() noexcept;
  void finish() noexcept;
  void cancel() noexcept;
  void reset() noexcept;
  void block() noexcept;
  bool target(double position) noexcept;
  std::optional<int> update(const FieldFeedback& feedback, int displayWidth, Clock::time_point now) noexcept;
  bool pending() const noexcept { return target_.has_value() || pending_.has_value(); }
  bool blocked() const noexcept { return blocked_; }

private:
  struct Pending {
    double before;
    double expected;
    Clock::time_point sent;
  };
  std::optional<double> target_;
  std::optional<Pending> pending_;
  std::uintptr_t identity_ = 0;
  bool holding_ = false;
  bool blocked_ = false;
};

}
