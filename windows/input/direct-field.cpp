#include "windows/input/direct-field.h"

#include <algorithm>
#include <cmath>

namespace ift {

void DirectField::begin() noexcept {
  target_.reset();
  holding_ = true;
  blocked_ = false;
}

void DirectField::finish() noexcept {
  holding_ = false;
}

void DirectField::cancel() noexcept {
  target_.reset();
  holding_ = false;
}

void DirectField::reset() noexcept {
  cancel();
  pending_.reset();
  identity_ = 0;
  blocked_ = false;
}

void DirectField::block() noexcept {
  cancel();
  pending_.reset();
  blocked_ = true;
}

bool DirectField::target(double position) noexcept {
  if (!std::isfinite(position) || position < 0 || position > 1 || blocked_) return false;
  target_ = position;
  return true;
}

std::optional<int> DirectField::update(const FieldFeedback& feedback, int displayWidth,
                                     Clock::time_point now) noexcept {
  if (!feedback.identity || !std::isfinite(feedback.position) ||
      !std::isfinite(feedback.sensitivity) || feedback.sensitivity < 0.01 ||
      feedback.sensitivity > 100 || displayWidth < 1 || displayWidth > 65536) return {};
  if (identity_ != feedback.identity) {
    pending_.reset();
    identity_ = feedback.identity;
  }
  const double unit = feedback.sensitivity / displayWidth;
  const double tolerance = std::max(unit * 0.51, 0.000002);
  if (pending_) {
    if (std::abs(feedback.position - pending_->before) > 0.000001 ||
        std::abs(feedback.position - pending_->expected) <= tolerance) {
      pending_.reset();
    } else if (now - pending_->sent > std::chrono::milliseconds(100)) {
      // Do not accumulate more mouse motion while the game is ignoring input.
      block();
    } else return {};
  }
  if (!target_ || blocked_) return {};
  const double distance = (*target_ - feedback.position) / unit;
  if (!std::isfinite(distance) || std::abs(distance) > 1048576) {
    block();
    return {};
  }
  const int delta = static_cast<int>(std::lround(distance));
  if (!delta) {
    if (!holding_) target_.reset();
    return {};
  }
  pending_ = Pending{feedback.position, feedback.position + delta * unit, now};
  return delta;
}

}
