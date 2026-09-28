#include "windows/input/trace-input.h"

#include <filesystem>
#include <stdexcept>

namespace ift {

TraceInput::TraceInput(const std::wstring& path) {
  if (!path.empty()) {
    output_.open(std::filesystem::path(path));
    if (!output_) {
      throw std::runtime_error("Cannot open dry-run trace file");
    }
  }
}

bool TraceInput::key(std::uint8_t lane, bool down) noexcept {
  if (output_.is_open()) {
    output_ << (down ? "DOWN " : "UP ") << static_cast<int>(lane) << std::endl;
    return output_.good();
  }
  return true;
}

bool TraceInput::absolute(Point point) noexcept {
  if (output_.is_open()) {
    output_ << "ABS " << point.x << ' ' << point.y << std::endl;
    return output_.good();
  }
  return true;
}

bool TraceInput::relative(int deltaX) noexcept {
  if (output_.is_open()) {
    output_ << "REL " << deltaX << std::endl;
    return output_.good();
  }
  return true;
}

}
