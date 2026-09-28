#include "windows/config/key-bindings.h"

#include <iomanip>
#include <sstream>
#include <stdexcept>
#include <string>

namespace ift {

std::uint16_t keyScanCode(std::uint8_t key) {
  constexpr std::array<std::uint16_t, 15> punctuation{0, 0x39, 0x1c, 0x0f, 0x29, 0x28, 0x27, 0x33,
    0x34, 0x35, 0x2b, 0x1a, 0x1b, 0x0c, 0x0d};
  constexpr std::array<std::uint16_t, 26> letters{0x1e, 0x30, 0x2e, 0x20, 0x12, 0x21, 0x22,
    0x23, 0x17, 0x24, 0x25, 0x26, 0x32, 0x31, 0x18, 0x19, 0x10, 0x13, 0x1f, 0x14, 0x16,
    0x2f, 0x11, 0x2d, 0x15, 0x2c};
  constexpr std::array<std::uint16_t, 33> special{0x2a, 0x36, 0x38, 0xe038, 0x1d, 0xe01d,
    0xe05b, 0xe05c, 0xe05d, 0x01, 0xe04b, 0xe04d, 0xe048, 0xe050, 0x0e, 0xe051, 0xe049,
    0xe047, 0xe04f, 0xe052, 0xe053, 0x3a, 0xe045, 0xe037, 0x46, 0, 0xe01c, 0xe035,
    0x37, 0x4e, 0x4a, 0x53, 0};
  constexpr std::array<std::uint16_t, 10> numpad{0x52, 0x4f, 0x50, 0x51, 0x4b, 0x4c, 0x4d, 0x47, 0x48, 0x49};
  if (key < 15) return punctuation[key];
  if (key <= 40) return letters[key - 15];
  if (key <= 50) return static_cast<std::uint16_t>(key - 39);
  if (key <= 83) return special[key - 51];
  if (key <= 93) return numpad[key - 84];
  if (key <= 103) return static_cast<std::uint16_t>(key - 35);
  if (key == 104) return 0x57;
  if (key == 105) return 0x58;
  return 0;
}

KeyBindings parseGameBindings(std::string_view text) {
  if (text.size() > 65536) throw std::invalid_argument("Game preferences exceed 64 KiB");
  KeyBindings result{};
  std::array<bool, 6> found{};
  std::array<bool, 6> states{};
  std::istringstream lines{std::string(text)};
  std::string line;
  while (std::getline(lines, line)) {
    std::istringstream keyLine(line);
    std::string tag, name;
    if (!(keyLine >> tag >> std::quoted(name)) || tag != "k") continue;
    const bool isState = name.starts_with("keybind_state_BottomLane");
    const std::string_view prefix = isState ? "keybind_state_BottomLane" : "keybind_BottomLane";
    if (!name.starts_with(prefix)) continue;
    if (name.size() != prefix.size() + 1 || name.back() < '0' || name.back() > '5') {
      throw std::invalid_argument("Unknown game lane binding");
    }
    const auto index = static_cast<std::size_t>(name.back() - '0');
    if (!std::getline(lines, line)) throw std::invalid_argument("Incomplete game key binding");
    std::istringstream valueLine(line);
    int value = -1;
    std::string extra;
    if (!(valueLine >> tag >> value) || tag != "v" || (valueLine >> extra)) throw std::invalid_argument("Invalid game key binding value");
    if (isState) {
      if (states[index] || value != 0) throw std::invalid_argument("Unsupported game key binding state");
      states[index] = true;
    } else {
      if (found[index] || value < 1 || value > 105 || !keyScanCode(static_cast<std::uint8_t>(value))) {
        throw std::invalid_argument("Unsupported or duplicate game key binding");
      }
      found[index] = true;
      result[index] = static_cast<std::uint8_t>(value);
    }
  }
  for (std::size_t index = 0; index < found.size(); ++index) {
    if (!found[index] || !states[index]) throw std::invalid_argument("Game preferences do not contain six complete bindings");
  }
  return result;
}

std::uint64_t packBindings(const KeyBindings& bindings) {
  std::uint64_t packed = 0;
  for (const auto key : bindings) packed = (packed << 8) | key;
  return packed;
}

}
