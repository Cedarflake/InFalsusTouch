#pragma once

#include "windows/input/input-state.h"
#include "windows/input/control-group.h"

namespace ift {

class InputMixer {
public:
  explicit InputMixer(InputSink& sink) : sink_(sink) {}
  bool key(std::size_t device, std::uint8_t lane, bool down) noexcept;
  void field(std::size_t device, bool down) noexcept;
  void finishField(std::size_t device) noexcept;
  bool fieldPosition(std::size_t device, double normalized, Point point) noexcept;
  bool absolute(std::size_t device, Point point) noexcept;
  bool relative(std::size_t device, int delta) noexcept;
  bool ownsField(std::size_t device) const noexcept { return owner_ == device; }
  bool fieldBusy(std::size_t device) const noexcept { return owner_ != maxControllers && owner_ != device; }

private:
  void releaseField(std::size_t device, bool finish) noexcept;
  InputSink& sink_;
  std::array<std::uint32_t, 6> holders_{};
  std::array<std::uint64_t, maxControllers> fieldOrder_{};
  std::uint64_t nextOrder_ = 0;
  std::size_t owner_ = maxControllers;
  std::size_t finisher_ = maxControllers;
};

class ControllerInput final : public InputSink {
public:
  ControllerInput(InputMixer& mixer, std::size_t device) : mixer_(mixer), device_(device) {}
  bool key(std::uint8_t lane, bool down) noexcept override { return mixer_.key(device_, lane, down); }
  bool absolute(Point point) noexcept override { return mixer_.absolute(device_, point); }
  bool fieldPosition(double normalized, Point point) noexcept override {
    return mixer_.fieldPosition(device_, normalized, point);
  }
  bool relative(int delta) noexcept override { return mixer_.relative(device_, delta); }
  void field(bool down) noexcept override { mixer_.field(device_, down); }
  void finishField() noexcept override { mixer_.finishField(device_); }
  std::uint8_t fieldStatus() const noexcept {
    return mixer_.ownsField(device_) ? 4 : mixer_.fieldBusy(device_) ? 2 : 0;
  }

private:
  InputMixer& mixer_;
  std::size_t device_;
};

}
