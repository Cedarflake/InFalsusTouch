#include <Windows.h>

#include <cstdint>
#include <iostream>

namespace {

LRESULT CALLBACK receiveInput(HWND window, UINT message, WPARAM parameter, LPARAM data) {
  if (message == WM_CREATE) {
    const auto creation = reinterpret_cast<CREATESTRUCTW*>(data);
    SetWindowLongPtrW(window, GWLP_USERDATA, reinterpret_cast<LONG_PTR>(creation->lpCreateParams));
  }
  const auto identity = GetWindowLongPtrW(window, GWLP_USERDATA);
  if (message == WM_KEYDOWN || message == WM_KEYUP || message == WM_SYSKEYDOWN || message == WM_SYSKEYUP) {
    const bool down = message == WM_KEYDOWN || message == WM_SYSKEYDOWN;
    std::cout << "KEY " << identity << ' ' << (down ? "DOWN" : "UP") << ' '
      << parameter << ' ' << ((static_cast<std::uintptr_t>(data) >> 16) & 255) << std::endl;
    return 0;
  }
  if (message == WM_DESTROY && identity == 1) { PostQuitMessage(0); return 0; }
  return DefWindowProcW(window, message, parameter, data);
}

}

int main() {
  SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
  const auto instance = GetModuleHandleW(nullptr);
  WNDCLASSW type{};
  type.hInstance = instance;
  type.lpfnWndProc = receiveInput;
  type.lpszClassName = L"InFalsusTouchInputTest";
  type.hCursor = LoadCursorW(nullptr, IDC_ARROW);
  type.hbrBackground = reinterpret_cast<HBRUSH>(COLOR_WINDOW + 1);
  if (!RegisterClassW(&type)) return 1;
  constexpr DWORD style = WS_OVERLAPPEDWINDOW;
  RECT client{0, 0, 1000, 600};
  AdjustWindowRect(&client, style, FALSE);
  const auto primary = CreateWindowExW(0, type.lpszClassName, L"InFalsusTouch input acceptance",
    style, 60, 60, client.right - client.left, client.bottom - client.top, nullptr, nullptr, instance,
    reinterpret_cast<void*>(1));
  const auto secondary = CreateWindowExW(0, type.lpszClassName, L"InFalsusTouch focus-loss test",
    style, 100, 100, 400, 240, nullptr, nullptr, instance, reinterpret_cast<void*>(2));
  if (!primary || !secondary) return 1;
  std::cout << "WINDOW 1 0x" << std::hex << reinterpret_cast<std::uintptr_t>(primary) << '\n'
    << "WINDOW 2 0x" << reinterpret_cast<std::uintptr_t>(secondary) << std::dec << std::endl;
  ShowWindow(secondary, SW_SHOWNOACTIVATE);
  ShowWindow(primary, SW_SHOW);
  MSG message{};
  while (GetMessageW(&message, nullptr, 0, 0) > 0) {
    TranslateMessage(&message);
    DispatchMessageW(&message);
  }
  return 0;
}
