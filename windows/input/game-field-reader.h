#pragma once

#include <Windows.h>

#include <optional>
#include <string>
#include <type_traits>

#include "windows/input/direct-field.h"

namespace ift {

class GameFieldReader {
public:
  explicit GameFieldReader(HWND window);
  ~GameFieldReader();
  GameFieldReader(const GameFieldReader&) = delete;
  GameFieldReader& operator=(const GameFieldReader&) = delete;
  bool isGame() const noexcept { return isGame_; }
  bool supported() const noexcept { return module_ != 0; }
  const std::string& problem() const noexcept { return problem_; }
  std::optional<FieldFeedback> sample() const noexcept;

private:
  void initialize(HWND window);
  template <typename T> bool read(std::uintptr_t address, T& value) const noexcept {
    static_assert(std::is_trivially_copyable_v<T>);
    SIZE_T count = 0;
    return address >= 65536 && ReadProcessMemory(process_, reinterpret_cast<const void*>(address),
      &value, sizeof(value), &count) && count == sizeof(value);
  }
  HANDLE process_ = nullptr;
  std::uintptr_t module_ = 0;
  bool isGame_ = false;
  std::string problem_;
};

}
