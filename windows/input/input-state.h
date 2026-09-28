#pragma once

#include <array>
#include <cstdint>

#include "protocol/cpp/packet.h"
#include "windows/input/field-mapper.h"
#include "windows/config/key-bindings.h"

namespace ift {

class InputSink {
public:
  virtual ~InputSink() = default;
  virtual bool key(std::uint8_t lane, bool down) noexcept = 0;
  virtual bool absolute(Point clientPoint) noexcept = 0;
  virtual bool relative(int deltaX) noexcept = 0;
  virtual void field(bool down) noexcept { (void)down; }
  virtual void setBindings(const KeyBindings& bindings) { (void)bindings; }
};

class InputState {
public:
  InputState(InputSink& sink, FieldConfig config);
  ~InputState();
  InputState(const InputState&) = delete;
  InputState& operator=(const InputState&) = delete;

  void updateTarget(bool active, Rect client);
  void setControls(std::uint8_t mask);
  bool apply(const Packet& packet, double seconds);
  bool releaseAll() noexcept;
  bool isReady() const;
  std::uint8_t pressedMask() const;

private:
  InputSink& sink_;
  FieldConfig config_;
  Rect client_;
  RelativeMapper relative_;
  std::array<bool, 6> pressed_{};
  bool active_ = false;
  bool needsBarrier_ = true;
  std::uint8_t controls_ = 127;
};

}
