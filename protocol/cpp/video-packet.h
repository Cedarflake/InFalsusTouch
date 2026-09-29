#pragma once

#include <array>
#include <cstdint>
#include <span>
#include <vector>

namespace ift::video {

constexpr std::size_t headerSize = 64;
constexpr std::size_t requestSize = 8;
constexpr std::uint8_t version = 2;
constexpr std::uint32_t maxPayloadSize = 4 * 1024 * 1024;
enum class Type : std::uint8_t { config = 1, frame = 2, error = 3 };

struct Header {
  Type type = Type::frame;
  bool keyFrame = false;
  std::uint32_t payloadSize = 0;
  std::uint32_t sequence = 0;
  std::uint64_t captureTimestamp = 0;
  std::uint64_t encodeTimestamp = 0;
  std::uint64_t sendTimestamp = 0;
  std::uint64_t presentationUs = 0;
  std::uint16_t width = 1280;
  std::uint16_t height = 720;
  std::uint16_t fps = 60;
  std::uint32_t bitrate = 8'000'000;
};

using HeaderBytes = std::array<std::uint8_t, headerSize>;
using RequestBytes = std::array<std::uint8_t, requestSize>;
RequestBytes encodeRequest(std::uint16_t fps);
std::uint16_t decodeRequest(std::span<const std::uint8_t> bytes);
HeaderBytes encodeHeader(const Header& header);
Header decodeHeader(std::span<const std::uint8_t> bytes);
std::vector<std::uint8_t> toAnnexB(std::span<const std::uint8_t> bytes);
bool hasNal(std::span<const std::uint8_t> bytes, std::uint8_t type);
std::vector<std::uint8_t> extractParameterSets(std::span<const std::uint8_t> bytes);

}
