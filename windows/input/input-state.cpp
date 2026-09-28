#include "windows/input/input-state.h"

#include <stdexcept>

namespace ift {

InputState::InputState(InputSink& sink, FieldConfig config) : sink_(sink), config_(config) {
  config_.validate();
}

InputState::~InputState() {
  releaseAll();
}

void InputState::updateTarget(bool active, Rect client) {
  active_ = active && client.width > 0 && client.height > 0;
  client_ = client;
  if (!active_) {
    needsBarrier_ = true;
    if (!releaseAll()) {
      throw std::runtime_error("Key release failed after target lost focus");
    }
  }
}

bool InputState::apply(const Packet& packet, double seconds) {
  validatePacket(packet);
  if (packet.type == MessageType::releaseAll || packet.type == MessageType::hello) {
    if (!releaseAll()) {
      throw std::runtime_error("Failed to release injected keys");
    }
    if (packet.type == MessageType::releaseAll && active_) {
      needsBarrier_ = false;
    }
    return isReady();
  }
  if (!isReady() || packet.type == MessageType::ping) {
    return isReady();
  }
  if (packet.type == MessageType::laneDown || packet.type == MessageType::laneUp) {
    auto& pressed = pressed_[packet.lane - 1];
    const bool down = packet.type == MessageType::laneDown;
    if (pressed != down) {
      // Track a potentially injected DOWN even if the OS reports failure.
      if (down) {
        pressed = true;
      }
      if (!sink_.key(packet.lane, down)) {
        throw std::runtime_error("SendInput failed; check target privilege level");
      }
      pressed = down;
    }
  } else if (packet.type == MessageType::fieldAbsolute) {
    if (!sink_.absolute(mapField(packet.value, client_, config_))) {
      throw std::runtime_error("Absolute mouse injection failed");
    }
  } else if (packet.type == MessageType::fieldRelative) {
    const int delta = relative_.move(packet.value, client_.width, seconds, config_);
    if (delta != 0 && !sink_.relative(delta)) {
      throw std::runtime_error("Relative mouse injection failed");
    }
  }
  return true;
}

bool InputState::releaseAll() noexcept {
  bool released = true;
  for (std::size_t index = 0; index < pressed_.size(); ++index) {
    if (pressed_[index]) {
      if (sink_.key(static_cast<std::uint8_t>(index + 1), false)) {
        pressed_[index] = false;
      } else {
        released = false;
      }
    }
  }
  relative_.reset();
  return released;
}

bool InputState::isReady() const {
  return active_ && !needsBarrier_;
}

std::uint8_t InputState::pressedMask() const {
  std::uint8_t mask = 0;
  for (std::size_t index = 0; index < pressed_.size(); ++index) {
    if (pressed_[index]) {
      mask |= static_cast<std::uint8_t>(1u << index);
    }
  }
  return mask;
}

}
