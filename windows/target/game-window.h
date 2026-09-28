#pragma once

#include <Windows.h>

#include <string>
#include <vector>

#include "windows/config/host-options.h"

namespace ift {

struct WindowInfo {
  HWND handle = nullptr;
  std::wstring title;
};

std::vector<WindowInfo> listWindows();
void printWindows(const std::vector<WindowInfo>& windows);
HWND chooseWindow(const HostOptions& options);
void printTargetKeyboard(HWND window);

class GameWindow {
public:
  explicit GameWindow(HWND handle);
  bool activeClient(Rect& client) const;

private:
  HWND handle_;
  DWORD processId_ = 0;
};

}
