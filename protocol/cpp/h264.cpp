#include "protocol/cpp/video-packet.h"

#include <stdexcept>

namespace ift::video {
namespace {

std::size_t prefix(std::span<const std::uint8_t> bytes, std::size_t offset) {
  if (offset + 3 > bytes.size() || bytes[offset] != 0 || bytes[offset + 1] != 0) return 0;
  if (bytes[offset + 2] == 1) return 3;
  return offset + 4 <= bytes.size() && bytes[offset + 2] == 0 && bytes[offset + 3] == 1 ? 4 : 0;
}

std::vector<std::span<const std::uint8_t>> units(std::span<const std::uint8_t> bytes) {
  std::vector<std::span<const std::uint8_t>> output;
  std::size_t offset = 0;
  while (offset < bytes.size()) {
    const auto length = prefix(bytes, offset);
    if (!length) throw std::invalid_argument("H.264 Annex B start code missing");
    const auto start = offset + length;
    auto end = start;
    while (end < bytes.size() && !prefix(bytes, end)) ++end;
    if (end == start) throw std::invalid_argument("Empty H.264 NAL");
    if (bytes[start] & 0x80) throw std::invalid_argument("H.264 forbidden bit is set");
    output.push_back(bytes.subspan(start, end - start));
    offset = end;
  }
  return output;
}

void append(std::vector<std::uint8_t>& output, std::span<const std::uint8_t> nal) {
  if (nal.empty() || output.size() + nal.size() + 4 > maxPayloadSize) {
    throw std::invalid_argument("H.264 NAL size exceeds bounds");
  }
  output.insert(output.end(), {0, 0, 0, 1});
  output.insert(output.end(), nal.begin(), nal.end());
}

std::size_t nalLength(std::span<const std::uint8_t> bytes, std::size_t& offset, unsigned size) {
  if (offset + size > bytes.size()) throw std::invalid_argument("Truncated H.264 length");
  std::size_t length = 0;
  for (unsigned index = 0; index < size; ++index) length = (length << 8) | bytes[offset++];
  if (length == 0 || length > bytes.size() - offset) throw std::invalid_argument("Truncated H.264 NAL");
  return length;
}

}

std::vector<std::uint8_t> toAnnexB(std::span<const std::uint8_t> bytes) {
  if (bytes.empty() || bytes.size() > maxPayloadSize) throw std::invalid_argument("Invalid H.264 sample size");
  if (prefix(bytes, 0)) {
    units(bytes);
    return {bytes.begin(), bytes.end()};
  }
  std::vector<std::uint8_t> output;
  if (bytes.size() >= 7 && bytes[0] == 1) {
    if ((bytes[4] & 3) != 3) throw std::invalid_argument("Only four-byte AVC lengths are supported");
    std::size_t offset = 6;
    auto count = static_cast<unsigned>(bytes[5] & 31);
    for (unsigned group = 0; group < 2; ++group) {
      for (unsigned index = 0; index < count; ++index) {
        const auto length = nalLength(bytes, offset, 2);
        append(output, bytes.subspan(offset, length));
        offset += length;
      }
      if (group == 0) {
        if (offset >= bytes.size()) throw std::invalid_argument("AVC PPS count missing");
        count = bytes[offset++];
      }
    }
  } else {
    std::size_t offset = 0;
    while (offset < bytes.size()) {
      const auto length = nalLength(bytes, offset, 4);
      append(output, bytes.subspan(offset, length));
      offset += length;
    }
  }
  units(output);
  return output;
}

bool hasNal(std::span<const std::uint8_t> bytes, std::uint8_t type) {
  for (const auto nal : units(bytes)) if ((nal[0] & 31) == type) return true;
  return false;
}

std::vector<std::uint8_t> extractParameterSets(std::span<const std::uint8_t> bytes) {
  std::vector<std::uint8_t> output;
  for (const auto nal : units(bytes)) {
    if ((nal[0] & 31) == 7 || (nal[0] & 31) == 8) append(output, nal);
  }
  return output;
}

}
