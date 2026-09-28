#pragma once

#include "windows/input/input-state.h"
#include "windows/input/game-field-reader.h"

namespace ift {

class Win32Input : public InputSink {
public:
  explicit Win32Input(HWND window);
  bool key(std::uint8_t lane, bool down) noexcept override;
  bool absolute(Point clientPoint) noexcept override;
  bool relative(int deltaX) noexcept override;
  bool fieldPosition(double normalized, Point clientPoint) noexcept override;
  void field(bool down) noexcept override;
  void finishField() noexcept override;
  void tick() noexcept override;
  bool pendingField() const noexcept override { return direct_.pending(); }
  void setBindings(const KeyBindings& bindings) override { bindings_ = bindings; }

private:
  bool activeChart() const noexcept;
  HWND window_ = nullptr;
  DWORD processId_ = 0;
  GameFieldReader feedback_;
  DirectField direct_;
  std::optional<DirectField::Clock::time_point> missingFeedback_;
  KeyBindings bindings_ = defaultBindings;
  std::array<bool, 6> pressed_{};
};

}
