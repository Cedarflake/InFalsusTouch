#pragma once

#include <filesystem>
#include <optional>

#include "windows/config/key-bindings.h"

namespace ift {

std::filesystem::path defaultGamePreferences();

class GameBindings {
public:
  explicit GameBindings(std::filesystem::path path) : path_(std::move(path)) {}
  bool refresh();
  const KeyBindings& keys() const { return keys_; }
  std::uint8_t status() const { return status_; }

private:
  std::filesystem::path path_;
  std::optional<std::filesystem::file_time_type> modified_;
  KeyBindings keys_ = defaultBindings;
  std::uint8_t status_ = 1;
};

}
