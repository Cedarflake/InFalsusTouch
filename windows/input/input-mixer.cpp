#include "windows/input/input-mixer.h"

namespace ift {

bool InputMixer::key(std::size_t device, std::uint8_t lane, bool down) noexcept {
  auto& holders = holders_[lane - 1];
  const auto bit = std::uint32_t{1} << device;
  if (down) {
    const bool wasEmpty = holders == 0;
    // Keep a possibly injected DOWN tracked even when SendInput reports failure.
    holders |= bit;
    return !wasEmpty || sink_.key(lane, true);
  }
  if ((holders & bit) == 0) return true;
  if (holders == bit && !sink_.key(lane, false)) return false;
  holders &= ~bit;
  return true;
}

void InputMixer::field(std::size_t device, bool down) noexcept {
  if (down) {
    if (fieldOrder_[device] == 0) fieldOrder_[device] = ++nextOrder_;
    if (owner_ == maxControllers) {
      owner_ = device;
      finisher_ = maxControllers;
      sink_.field(true);
    }
    return;
  }
  releaseField(device, false);
}

void InputMixer::finishField(std::size_t device) noexcept {
  releaseField(device, true);
}

void InputMixer::releaseField(std::size_t device, bool finish) noexcept {
  fieldOrder_[device] = 0;
  if (owner_ != device) {
    if (finisher_ == device && !finish) {
      finisher_ = maxControllers;
      sink_.field(false);
    }
    return;
  }
  if (finish) sink_.finishField();
  else sink_.field(false);
  finisher_ = finish ? device : maxControllers;
  owner_ = maxControllers;
  for (std::size_t index = 0; index < fieldOrder_.size(); ++index) {
    if (fieldOrder_[index] != 0 &&
        (owner_ == maxControllers || fieldOrder_[index] < fieldOrder_[owner_])) owner_ = index;
  }
  if (owner_ != maxControllers) {
    finisher_ = maxControllers;
    sink_.field(true);
  }
}

bool InputMixer::fieldPosition(std::size_t device, double normalized, Point point) noexcept {
  field(device, true);
  return !ownsField(device) || sink_.fieldPosition(normalized, point);
}

bool InputMixer::absolute(std::size_t device, Point point) noexcept {
  field(device, true);
  return !ownsField(device) || sink_.absolute(point);
}

bool InputMixer::relative(std::size_t device, int delta) noexcept {
  field(device, true);
  return !ownsField(device) || sink_.relative(delta);
}

}
