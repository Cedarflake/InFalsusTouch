#pragma once

#include <array>
#include <cstdint>
#include <string_view>

namespace ift {

using KeyBindings = std::array<std::uint8_t, 6>;
constexpr KeyBindings defaultBindings{51, 15, 33, 18, 20, 1};

// Unity Input System Key uses physical US positions. High byte E0 marks an extended scan.
std::uint16_t keyScanCode(std::uint8_t unityKey);
KeyBindings parseGameBindings(std::string_view text);
std::uint64_t packBindings(const KeyBindings& bindings);

}
