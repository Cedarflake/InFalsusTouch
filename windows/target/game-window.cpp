#include "windows/target/game-window.h"

#include <algorithm>
#include <cwctype>
#include <iostream>
#include <limits>
#include <stdexcept>

namespace ift {
namespace {

BOOL CALLBACK collectWindow(HWND handle, LPARAM context) {
  if (!IsWindowVisible(handle) || GetWindow(handle, GW_OWNER) != nullptr) {
    return TRUE;
  }
  const int length = GetWindowTextLengthW(handle);
  DWORD processId = 0;
  GetWindowThreadProcessId(handle, &processId);
  if (length <= 0 || processId == GetCurrentProcessId()) {
    return TRUE;
  }
  std::wstring title(static_cast<std::size_t>(length) + 1, L'\0');
  const int copied = GetWindowTextW(handle, title.data(), length + 1);
  title.resize(static_cast<std::size_t>(copied));
  auto& windows = *reinterpret_cast<std::vector<WindowInfo>*>(context);
  windows.push_back({handle, std::move(title)});
  return TRUE;
}

std::wstring lowercase(std::wstring text) {
  std::transform(text.begin(), text.end(), text.begin(), [](wchar_t value) {
    return static_cast<wchar_t>(std::towlower(value));
  });
  return text;
}

}

std::vector<WindowInfo> listWindows() {
  std::vector<WindowInfo> windows;
  if (!EnumWindows(collectWindow, reinterpret_cast<LPARAM>(&windows))) {
    throw std::runtime_error("Window enumeration failed");
  }
  return windows;
}

void printWindows(const std::vector<WindowInfo>& windows) {
  for (std::size_t index = 0; index < windows.size(); ++index) {
    std::wcout << index + 1 << L". 0x" << std::hex
               << reinterpret_cast<std::uintptr_t>(windows[index].handle)
               << std::dec << L"  " << windows[index].title << L'\n';
  }
}

HWND chooseWindow(const HostOptions& options) {
  if (options.window != 0) {
    const auto handle = reinterpret_cast<HWND>(options.window);
    if (!IsWindow(handle)) {
      throw std::invalid_argument("Selected window handle no longer exists");
    }
    return handle;
  }
  const auto windows = listWindows();
  std::vector<WindowInfo> matches;
  const auto query = lowercase(options.title);
  for (const auto& window : windows) {
    if (!query.empty() && lowercase(window.title).find(query) != std::wstring::npos) {
      matches.push_back(window);
    }
  }
  if (matches.size() == 1) {
    std::wcout << L"Selected: " << matches.front().title << L'\n';
    return matches.front().handle;
  }
  if (windows.empty()) {
    throw std::runtime_error("No visible windows; start In Falsus first");
  }
  printWindows(windows);
  std::wcout << L"Select a window number (0 cancels): " << std::flush;
  std::size_t choice = 0;
  if (!(std::wcin >> choice) || choice == 0 || choice > windows.size()) {
    throw std::runtime_error("No valid target selected; use --list / --window");
  }
  std::wcin.ignore(std::numeric_limits<std::streamsize>::max(), L'\n');
  return windows[choice - 1].handle;
}

GameWindow::GameWindow(HWND handle) : handle_(handle) {
  if (handle_) {
    GetWindowThreadProcessId(handle_, &processId_);
  }
}

bool GameWindow::activeClient(Rect& client) const {
  DWORD currentProcess = 0;
  GetWindowThreadProcessId(handle_, &currentProcess);
  if (!IsWindow(handle_) || currentProcess != processId_ || IsIconic(handle_) ||
      !IsWindowVisible(handle_) || GetAncestor(GetForegroundWindow(), GA_ROOT) != handle_) {
    return false;
  }
  RECT bounds{};
  POINT origin{};
  if (!GetClientRect(handle_, &bounds) || !ClientToScreen(handle_, &origin)) {
    return false;
  }
  client = {origin.x, origin.y, bounds.right - bounds.left, bounds.bottom - bounds.top};
  return client.width > 0 && client.height > 0;
}

}
