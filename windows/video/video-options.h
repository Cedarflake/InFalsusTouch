#pragma once

#include <cstdint>

namespace ift {

struct VideoOptions {
  bool enabled = true;
  std::uint16_t port = 27183;
  std::uint16_t width = 1280;
  std::uint16_t height = 720;
  std::uint16_t fps = 60;
  std::uint32_t bitrate = 8'000'000;
};

std::uint64_t performanceNanoseconds();

}
