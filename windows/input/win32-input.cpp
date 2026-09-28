#include "windows/input/win32-input.h"

#include <Windows.h>

#include <array>
#include <iostream>

namespace ift {
namespace {

bool sendRelative(int deltaX) noexcept {
  INPUT input{};
  input.type = INPUT_MOUSE;
  input.mi.dx = deltaX;
  input.mi.dwFlags = MOUSEEVENTF_MOVE;
  return SendInput(1, &input, sizeof(INPUT)) == 1;
}

}

Win32Input::Win32Input(HWND window) : window_(window), feedback_(window) {
  GetWindowThreadProcessId(window_, &processId_);
  if (feedback_.isGame()) {
    if (feedback_.supported()) std::cout << "Absolute Field: live game position and sensitivity (read-only)\n";
    else std::cerr << "Absolute Field: " << feedback_.problem() << '\n';
  }
}

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
  direct_.reset();
  return sendRelative(deltaX);
}

bool Win32Input::activeChart() const noexcept {
  DWORD process = 0;
  GetWindowThreadProcessId(window_, &process);
  RECT clip{}, client{};
  POINT origin{};
  return process == processId_ && GetAncestor(GetForegroundWindow(), GA_ROOT) == window_ &&
    GetClipCursor(&clip) && GetClientRect(window_, &client) && ClientToScreen(window_, &origin) &&
    clip.right - clip.left <= 2 && clip.bottom - clip.top <= 2 &&
    clip.left >= origin.x && clip.left < origin.x + client.right &&
    clip.top >= origin.y && clip.top < origin.y + client.bottom;
}

bool Win32Input::fieldPosition(double normalized, Point clientPoint) noexcept {
  if (!feedback_.isGame()) return feedback_.problem().empty() && absolute(clientPoint);
  if (!activeChart()) { direct_.reset(); return absolute(clientPoint); }
  if (!feedback_.supported() || !direct_.target(normalized)) return false;
  tick();
  return !direct_.blocked();
}

void Win32Input::field(bool down) noexcept {
  if (down) direct_.begin();
  else direct_.cancel();
  missingFeedback_.reset();
}

void Win32Input::finishField() noexcept {
  direct_.finish();
}

void Win32Input::tick() noexcept {
  if (!direct_.pending()) return;
  if (!activeChart()) { direct_.reset(); missingFeedback_.reset(); return; }
  const auto now = DirectField::Clock::now();
  const auto feedback = feedback_.sample();
  if (!feedback) {
    if (!missingFeedback_) missingFeedback_ = now;
    else if (now - *missingFeedback_ > std::chrono::milliseconds(100)) {
      direct_.block();
      missingFeedback_.reset();
      std::cerr << "Absolute Field: game position unavailable; touch again in an active chart\n";
    }
    return;
  }
  missingFeedback_.reset();
  const bool wasBlocked = direct_.blocked();
  const auto delta = direct_.update(*feedback, GetSystemMetrics(SM_CXSCREEN), now);
  if (!wasBlocked && direct_.blocked()) {
    std::cerr << "Absolute Field: game did not consume mouse input; lift and touch again\n";
  }
  if (delta && !sendRelative(*delta)) {
    direct_.block();
    std::cerr << "Absolute Field: SendInput failed; check target privilege level\n";
  }
}

}
