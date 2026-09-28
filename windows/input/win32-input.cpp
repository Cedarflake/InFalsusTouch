#include "windows/input/win32-input.h"

#include <Windows.h>

#include <array>

namespace ift {

bool Win32Input::key(std::uint8_t lane, bool down) noexcept {
  if (lane < 1 || lane > bindings_.size()) {
    return false;
  }
  const auto index = static_cast<std::size_t>(lane - 1);
  const auto scan = keyScanCode(bindings_[index]);
  if (!scan) return false;
  if (pressed_[index] == down) return true;
  const bool heldElsewhere = [&] {
    for (std::size_t other = 0; other < pressed_.size(); ++other) {
      if (other != index && pressed_[other] && bindings_[other] == bindings_[index]) return true;
    }
    return false;
  }();
  if (heldElsewhere) { pressed_[index] = down; return true; }
  if (down) pressed_[index] = true;
  INPUT input{};
  input.type = INPUT_KEYBOARD;
  input.ki.wScan = scan & 0xff;
  input.ki.dwFlags = KEYEVENTF_SCANCODE | (scan > 0xff ? KEYEVENTF_EXTENDEDKEY : 0u) | (down ? 0u : KEYEVENTF_KEYUP);
  if (SendInput(1, &input, sizeof(INPUT)) != 1) return false;
  pressed_[index] = down;
  return true;
}

bool Win32Input::absolute(Point clientPoint) noexcept {
  const Rect desktop{
    GetSystemMetrics(SM_XVIRTUALSCREEN), GetSystemMetrics(SM_YVIRTUALSCREEN),
    GetSystemMetrics(SM_CXVIRTUALSCREEN), GetSystemMetrics(SM_CYVIRTUALSCREEN),
  };
  if (desktop.width <= 1 || desktop.height <= 1) {
    return false;
  }
  const Point normalized = normalizeDesktop(clientPoint, desktop);
  INPUT input{};
  input.type = INPUT_MOUSE;
  input.mi.dx = normalized.x;
  input.mi.dy = normalized.y;
  input.mi.dwFlags = MOUSEEVENTF_MOVE | MOUSEEVENTF_ABSOLUTE | MOUSEEVENTF_VIRTUALDESK;
  return SendInput(1, &input, sizeof(INPUT)) == 1;
}

bool Win32Input::relative(int deltaX) noexcept {
  INPUT input{};
  input.type = INPUT_MOUSE;
  input.mi.dx = deltaX;
  input.mi.dwFlags = MOUSEEVENTF_MOVE;
  return SendInput(1, &input, sizeof(INPUT)) == 1;
}

}
