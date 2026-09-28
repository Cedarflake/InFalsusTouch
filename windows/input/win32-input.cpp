#include "windows/input/win32-input.h"

#include <Windows.h>

#include <array>

namespace ift {

bool Win32Input::key(std::uint8_t lane, bool down) noexcept {
  constexpr std::array<WORD, 6> scanCodes{0x2a, 0x1e, 0x1f, 0x20, 0x21, 0x39};
  if (lane < 1 || lane > scanCodes.size()) {
    return false;
  }
  INPUT input{};
  input.type = INPUT_KEYBOARD;
  input.ki.wScan = scanCodes[lane - 1];
  input.ki.dwFlags = KEYEVENTF_SCANCODE | (down ? 0u : KEYEVENTF_KEYUP);
  return SendInput(1, &input, sizeof(INPUT)) == 1;
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
