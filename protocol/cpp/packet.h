#pragma once

#include <array>
#include <cstdint>
#include <span>
#include <stdexcept>

namespace ift {

constexpr std::size_t packetSize = 32;
using PacketBytes = std::array<std::uint8_t, packetSize>;

enum class MessageType : std::uint8_t {
  hello = 1,
  laneDown = 2,
  laneUp = 3,
  fieldAbsolute = 4,
  fieldRelative = 5,
  releaseAll = 6,
  ping = 7,
  ack = 128,
};

struct Packet {
  MessageType type = MessageType::hello;
  std::uint8_t lane = 0;
  std::uint8_t status = 0;
  std::uint32_t sequence = 1;
  float value = 0;
  std::uint64_t timestampNs = 0;
};

class ProtocolError : public std::runtime_error {
public:
  using std::runtime_error::runtime_error;
};

void validatePacket(const Packet& packet);
PacketBytes encodePacket(const Packet& packet);
Packet decodePacket(std::span<const std::uint8_t> bytes);

}
