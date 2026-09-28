#include "windows/config/field-calibration.h"

#include <iostream>
#include <stdexcept>
#include <string>

namespace ift {
namespace {

Rect clientRectangle(HWND window) {
  RECT bounds{};
  POINT origin{};
  if (!IsWindow(window) || IsIconic(window) || !IsWindowVisible(window) ||
      !GetClientRect(window, &bounds) || !ClientToScreen(window, &origin)) {
    throw std::runtime_error("Keep the selected game window visible while calibrating");
  }
  return {origin.x, origin.y, bounds.right - bounds.left, bounds.bottom - bounds.top};
}

}

FieldConfig calibrateGameWindow(HWND window, const FieldConfig& base) {
  const auto client = clientRectangle(window);
  DWORD processId = 0;
  GetWindowThreadProcessId(window, &processId);
  std::wcout << L"Field calibration: keep this console focused and the game visible beside it.\n"
    L"Move the mouse without clicking, then press Enter in this console. Type q to cancel.\n"
    L"No input is sent to the game during calibration. Do not move or resize the game window.\n";
  const auto point = [&](const wchar_t* prompt) {
    std::wcout << prompt << L" Press Enter: " << std::flush;
    std::wstring answer;
    if (!std::getline(std::wcin, answer) || answer == L"q" || answer == L"Q") {
      throw std::runtime_error("Calibration cancelled; profile unchanged");
    }
    const auto current = clientRectangle(window);
    DWORD currentProcess = 0;
    GetWindowThreadProcessId(window, &currentProcess);
    if (processId != currentProcess || current.x != client.x || current.y != client.y ||
        current.width != client.width || current.height != client.height) {
      throw std::runtime_error("Game window moved, resized or closed; calibrate again");
    }
    POINT cursor{};
    if (!GetCursorPos(&cursor)) throw std::runtime_error("Cannot read cursor position");
    return Point{cursor.x, cursor.y};
  };
  const auto left = point(L"Point to the LEFT endpoint of the game Field.");
  const auto right = point(L"Point to the RIGHT endpoint of the game Field.");
  const auto vertical = point(L"Point to the desired fixed HEIGHT inside the game Field.");
  auto result = calibrateField(base, client, left, right, vertical);
  std::wcout << L"Field: left=" << result.left << L", right=" << result.right << L", y=" << result.y << L'\n';
  return result;
}

}
