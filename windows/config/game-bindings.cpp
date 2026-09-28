#include "windows/config/game-bindings.h"

#include <Windows.h>
#include <ShlObj.h>

#include <fstream>
#include <iostream>
#include <iterator>
#include <stdexcept>

namespace ift {

std::filesystem::path defaultGamePreferences() {
  PWSTR path = nullptr;
  if (FAILED(SHGetKnownFolderPath(FOLDERID_LocalAppDataLow, 0, nullptr, &path))) {
    throw std::runtime_error("Cannot locate LocalLow game preferences");
  }
  const auto result = std::filesystem::path(path) / L"lowiro" / L"infalsus" / L"userV2.prefs";
  CoTaskMemFree(path);
  return result;
}

bool GameBindings::refresh() {
  if (path_.empty()) return false;
  const auto previousStatus = status_;
  const auto previousKeys = keys_;
  try {
    if (!std::filesystem::exists(path_)) {
      if (modified_) throw std::runtime_error("Game preferences disappeared; input paused until they return");
      status_ = 1;
    } else {
      const auto stamp = std::filesystem::last_write_time(path_);
      if (modified_ == stamp && status_ == 0) return false;
      if (std::filesystem::file_size(path_) > 65536) throw std::runtime_error("Game preferences exceed 64 KiB");
      std::ifstream stream(path_, std::ios::binary);
      if (!stream) throw std::runtime_error("Cannot read game preferences");
      const std::string text{std::istreambuf_iterator<char>(stream), std::istreambuf_iterator<char>()};
      if (stream.bad()) throw std::runtime_error("Failed to read game preferences");
      keys_ = parseGameBindings(text);
      modified_ = stamp;
      status_ = 0;
    }
  } catch (const std::exception& error) {
    if (status_ != 2) std::cerr << "Key sync: " << error.what() << std::endl;
    status_ = 2;
  }
  return status_ != previousStatus || keys_ != previousKeys;
}

}
