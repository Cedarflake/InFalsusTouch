#include "protocol/cpp/packet.h"

#include <algorithm>
#include <bit>
#include <cmath>

namespace ift {
namespace {

constexpr std::array<std::uint8_t, 4> magic{0x49, 0x46, 0x54, 0x31};

void writeInteger(PacketBytes& bytes, std::size_t offset,
                  std::uint64_t value, std::size_t size) {
  for (std::size_t index = 0; index < size; ++index) {
    bytes[offset + size - index - 1] = static_cast<std::uint8_t>(value & 0xff);
    value >>= 8;
  }
}

std::uint64_t readInteger(std::span<const std::uint8_t> bytes,
                          std::size_t offset, std::size_t size) {
  std::uint64_t value = 0;
  for (std::size_t index = 0; index < size; ++index) {
    value = (value << 8) | bytes[offset + index];
  }
  return value;
}

}

void validatePacket(const Packet& packet) {
  if (packet.type == MessageType::configuration) {
    if (packet.lane > 127 || packet.status > 2 || packet.sequence == 0 ||
        !std::isfinite(packet.value) || packet.value < 1 || packet.value > 7 || std::floor(packet.value) != packet.value ||
        (packet.timestampNs >> 48) != 0) throw ProtocolError("Invalid controller configuration");
    for (unsigned shift = 0; shift < 48; shift += 8) {
      const auto key = (packet.timestampNs >> shift) & 0xff;
      if (key == 0 || key > 105) throw ProtocolError("Invalid binding code");
    }
    return;
  }
  const auto type = static_cast<std::uint8_t>(packet.type);
  if ((type < 1 || type > 11) && packet.type != MessageType::ack) {
    throw ProtocolError("Unknown message type");
  }
  const bool isLane = packet.type == MessageType::laneDown ||
                      packet.type == MessageType::laneUp;
  if ((isLane && (packet.lane < 1 || packet.lane > 6)) ||
      (!isLane && packet.lane != 0)) {
    throw ProtocolError("Invalid lane");
  }
  if ((packet.status != 0 && packet.status != 1 && packet.status != 2 && packet.status != 4) ||
      (packet.type != MessageType::ack && packet.status != 0)) {
    throw ProtocolError("Invalid status");
  }
  if (!std::isfinite(packet.value)) {
    throw ProtocolError("Nonfinite Field value");
  }
  if (packet.type == MessageType::fieldAbsolute) {
    if (packet.value < 0 || packet.value > 1) {
      throw ProtocolError("Absolute Field value outside [0, 1]");
    }
  } else if (packet.type == MessageType::fieldRelative) {
    if (packet.value < -1 || packet.value > 1) {
      throw ProtocolError("Relative Field value outside [-1, 1]");
    }
  } else if (packet.type == MessageType::assignControls) {
    if (packet.value < 0 || packet.value > 127 || std::floor(packet.value) != packet.value) throw ProtocolError("Invalid assignment mask");
  } else if (packet.type == MessageType::videoFrameRate || (packet.type == MessageType::hello && packet.value != 0)) {
    if (packet.value < 24 || packet.value > 120 || std::floor(packet.value) != packet.value) throw ProtocolError("Invalid video frame rate");
  } else if (packet.value != 0) {
    throw ProtocolError("Unexpected value");
  }
}

PacketBytes encodePacket(const Packet& packet) {
  validatePacket(packet);
  PacketBytes bytes{};
  std::copy(magic.begin(), magic.end(), bytes.begin());
  bytes[4] = 3;
  bytes[5] = static_cast<std::uint8_t>(packet.type);
  bytes[6] = packet.lane;
  bytes[7] = packet.status;
  writeInteger(bytes, 8, packet.sequence, 4);
  writeInteger(bytes, 12, std::bit_cast<std::uint32_t>(packet.value), 4);
  writeInteger(bytes, 16, packet.timestampNs, 8);
  return bytes;
}

Packet decodePacket(std::span<const std::uint8_t> bytes) {
  if (bytes.size() != packetSize) {
    throw ProtocolError("Incorrect packet size");
  }
  if (!std::equal(magic.begin(), magic.end(), bytes.begin()) || bytes[4] != 3) {
    throw ProtocolError("Unsupported magic/version; update both Host and app");
  }
  if (readInteger(bytes, 24, 8) != 0) {
    throw ProtocolError("Reserved bytes must be zero");
  }
  Packet packet{
    static_cast<MessageType>(bytes[5]), bytes[6], bytes[7],
    static_cast<std::uint32_t>(readInteger(bytes, 8, 4)),
    std::bit_cast<float>(static_cast<std::uint32_t>(readInteger(bytes, 12, 4))),
    readInteger(bytes, 16, 8),
  };
  validatePacket(packet);
  return packet;
}

}
