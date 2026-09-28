#include "protocol/cpp/video-packet.h"

#include <stdexcept>

namespace ift::video {
namespace {

void put(HeaderBytes& bytes, std::size_t offset, std::uint64_t value, unsigned size) {
  for (unsigned index = 0; index < size; ++index) {
    bytes[offset + size - index - 1] = static_cast<std::uint8_t>(value >> (index * 8));
  }
}

std::uint64_t get(std::span<const std::uint8_t> bytes, std::size_t offset, unsigned size) {
  std::uint64_t value = 0;
  for (unsigned index = 0; index < size; ++index) value = (value << 8) | bytes[offset + index];
  return value;
}

void validate(const Header& header) {
  if (header.type != Type::config && header.type != Type::frame && header.type != Type::error) {
    throw std::invalid_argument("Unknown video packet type");
  }
  if (header.payloadSize == 0 || header.payloadSize > maxPayloadSize ||
      (header.type == Type::config && header.payloadSize > 65536) ||
      (header.type == Type::error && header.payloadSize > 4096)) {
    throw std::invalid_argument("Video payload length exceeds bounds");
  }
  if (header.width < 128 || header.width > 1920 || header.width % 2 ||
      header.height < 128 || header.height > 1080 || header.height % 2 ||
      header.fps < 24 || header.fps > 60 || header.bitrate < 500'000 || header.bitrate > 40'000'000) {
    throw std::invalid_argument("Unsupported video format");
  }
  if (header.type != Type::frame && header.keyFrame) throw std::invalid_argument("Unexpected key-frame flag");
  if (header.type == Type::frame && (header.captureTimestamp == 0 ||
      header.encodeTimestamp < header.captureTimestamp || header.sendTimestamp < header.encodeTimestamp ||
      header.presentationUs != header.captureTimestamp / 1000)) {
    throw std::invalid_argument("Invalid video timestamps");
  }
}

}

HeaderBytes encodeHeader(const Header& header) {
  validate(header);
  HeaderBytes bytes{};
  bytes[0] = 'I'; bytes[1] = 'F'; bytes[2] = 'V'; bytes[3] = '1';
  bytes[4] = 1;
  bytes[5] = static_cast<std::uint8_t>(header.type);
  bytes[7] = header.keyFrame ? 1 : 0;
  put(bytes, 8, header.payloadSize, 4);
  put(bytes, 12, header.sequence, 4);
  put(bytes, 16, header.captureTimestamp, 8);
  put(bytes, 24, header.encodeTimestamp, 8);
  put(bytes, 32, header.sendTimestamp, 8);
  put(bytes, 40, header.presentationUs, 8);
  put(bytes, 48, header.width, 2);
  put(bytes, 50, header.height, 2);
  put(bytes, 52, header.fps, 2);
  put(bytes, 56, header.bitrate, 4);
  return bytes;
}

Header decodeHeader(std::span<const std::uint8_t> bytes) {
  if (bytes.size() != headerSize || get(bytes, 0, 4) != 0x49465631 || bytes[4] != 1 ||
      bytes[6] != 0 || bytes[7] > 1 || get(bytes, 54, 2) != 0 || get(bytes, 60, 4) != 0) {
    throw std::invalid_argument("Invalid video packet header");
  }
  Header header;
  header.type = static_cast<Type>(bytes[5]);
  header.keyFrame = bytes[7] == 1;
  header.payloadSize = static_cast<std::uint32_t>(get(bytes, 8, 4));
  header.sequence = static_cast<std::uint32_t>(get(bytes, 12, 4));
  header.captureTimestamp = get(bytes, 16, 8);
  header.encodeTimestamp = get(bytes, 24, 8);
  header.sendTimestamp = get(bytes, 32, 8);
  header.presentationUs = get(bytes, 40, 8);
  header.width = static_cast<std::uint16_t>(get(bytes, 48, 2));
  header.height = static_cast<std::uint16_t>(get(bytes, 50, 2));
  header.fps = static_cast<std::uint16_t>(get(bytes, 52, 2));
  header.bitrate = static_cast<std::uint32_t>(get(bytes, 56, 4));
  validate(header);
  return header;
}

}
