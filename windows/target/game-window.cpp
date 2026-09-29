#include "windows/target/game-window.h"

#include <algorithm>
#include <array>
#include <cwctype>
#include <iostream>
#include <limits>
#include <stdexcept>

namespace ift {
namespace {

bool isGameProcess(DWORD processId) {
  const auto process = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, FALSE, processId);
  if (!process) return false;
  std::array<wchar_t, 32768> path{};
  DWORD length = static_cast<DWORD>(path.size());
  const bool identified = QueryFullProcessImageNameW(process, 0, path.data(), &length) != FALSE;
  CloseHandle(process);
  if (!identified) return false;
  const std::wstring executable(path.data(), length);
  const auto name = executable.substr(executable.find_last_of(L"\\/") + 1);
  return _wcsicmp(name.c_str(), L"infalsus.exe") == 0;
}

std::string utf8(const std::wstring& text) {
  if (text.empty()) return {};
  const auto length = static_cast<int>(text.size());
  const int bytes = WideCharToMultiByte(CP_UTF8, 0, text.data(), length, nullptr, 0, nullptr, nullptr);
  if (!bytes) throw std::runtime_error("Cannot encode window title");
  std::string result(static_cast<std::size_t>(bytes), '\0');
  if (!WideCharToMultiByte(CP_UTF8, 0, text.data(), length, result.data(), bytes, nullptr, nullptr)) {
    throw std::runtime_error("Cannot encode window title");
  }
  return result;
}

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
  windows.push_back({handle, std::move(title), isGameProcess(processId)});
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
    std::cout << index + 1 << ". 0x" << std::hex
               << reinterpret_cast<std::uintptr_t>(windows[index].handle)
               << std::dec << "  " << utf8(windows[index].title) << '\n';
  }
}

std::vector<WindowInfo> matchingWindows(const std::vector<WindowInfo>& windows, const std::wstring& title) {
  const auto query = lowercase(title);
  std::vector<WindowInfo> matches, exact;
  for (const auto& window : windows) {
    const auto candidate = lowercase(window.title);
    const bool matchesQuery = query == L"in falsus" ? window.isGame :
      !query.empty() && candidate.find(query) != std::wstring::npos;
    if (!matchesQuery) continue;
    matches.push_back(window);
    if (candidate == query) exact.push_back(window);
  }
  return exact.empty() ? matches : exact;
}

HWND chooseWindow(const HostOptions& options) {
  if (options.window != 0) {
    const auto handle = reinterpret_cast<HWND>(options.window);
    if (!IsWindow(handle)) {
      throw std::invalid_argument("Selected window handle no longer exists");
    }
    return handle;
  }
  const auto matches = matchingWindows(listWindows(), options.title);
  if (matches.size() == 1) {
    std::cout << "Selected: " << utf8(matches.front().title) << '\n';
    return matches.front().handle;
  }
  if (matches.empty()) {
    throw std::runtime_error(lowercase(options.title) == L"in falsus" ?
      "In Falsus game window not found; start the game first. Use --list / --window for manual selection" :
      "No window matches the requested title; use --list / --window for manual selection");
  }
  printWindows(matches);
  std::cout << "Select a window number (0 cancels): " << std::flush;
  std::size_t choice = 0;
  if (!(std::wcin >> choice) || choice == 0 || choice > matches.size()) {
    throw std::runtime_error("No valid target selected; use --list / --window");
  }
  std::wcin.ignore(std::numeric_limits<std::streamsize>::max(), L'\n');
  return matches[choice - 1].handle;
}

GameWindow::GameWindow(HWND handle) : handle_(handle) {
  if (handle_) {
    GetWindowThreadProcessId(handle_, &processId_);
  }
}

void printTargetKeyboard(HWND window) {
  const auto thread = GetWindowThreadProcessId(window, nullptr);
  const auto layout = reinterpret_cast<std::uintptr_t>(GetKeyboardLayout(thread));
  std::wcout << L"Game keyboard layout: 0x" << std::hex << layout << std::dec << L'\n';
  const auto language = PRIMARYLANGID(LOWORD(layout));
  if (language == LANG_CHINESE || language == LANG_JAPANESE || language == LANG_KOREAN) {
    std::wcout << L"Input-method warning: select a plain US English keyboard layout for the game.\n"
      L"An IME can consume lane keys; its English typing mode can switch back when the Shift lane is tapped.\n";
  }
  std::wcout << std::flush;
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
