#include "tests/test-support.h"

#include <bit>
#include <fstream>
#include <limits>
#include <sstream>

void protocolTests() {
  using namespace ift;
  std::ifstream vectors(std::string(IFT_SOURCE_DIR) + "/protocol/golden-vectors.txt");
  check(vectors.good(), "Golden vectors missing");
  std::string line;
  int count = 0;
  while (std::getline(vectors, line)) {
    if (line.empty() || line[0] == '#') {
      continue;
    }
    std::istringstream row(line);
    std::string name;
    std::string hex;
    row >> name >> hex;
    check(hex.size() == 64, "Bad golden vector length");
    PacketBytes bytes{};
    for (std::size_t index = 0; index < bytes.size(); ++index) {
      bytes[index] = static_cast<std::uint8_t>(std::stoul(hex.substr(index * 2, 2), nullptr, 16));
    }
    const auto packet = decodePacket(bytes);
    check(encodePacket(packet) == bytes, "Golden vector roundtrip mismatch");
    if (name == "absoluteHalf") {
      check(packet.value == 0.5f && packet.sequence == 4, "Float endian mismatch");
    }
    if (name == "hello") {
      check(packet.timestampNs == 0x0102030405060708ULL, "Timestamp endian mismatch");
    }
    ++count;
  }
  check(count == 12, "Missing protocol vectors");

  const auto good = encodePacket({MessageType::hello});
  for (std::size_t size = 0; size < packetSize; ++size) {
    expectError<ProtocolError>([&] { decodePacket(std::span(good).first(size)); });
  }
  for (const std::size_t offset : {0u, 4u, 5u, 6u, 7u, 24u, 31u}) {
    auto bad = good;
    bad[offset] = 255;
    expectError<ProtocolError>([&] { decodePacket(bad); });
  }
  for (const float value : {std::numeric_limits<float>::quiet_NaN(),
                            std::numeric_limits<float>::infinity(), -0.1f, 1.1f}) {
    expectError<ProtocolError>([&] { encodePacket({MessageType::fieldAbsolute, 0, 0, 1, value}); });
  }
  expectError<ProtocolError>([] { encodePacket({MessageType::laneDown, 0}); });
  expectError<ProtocolError>([] { encodePacket({MessageType::laneUp, 7}); });
  expectError<ProtocolError>([] { encodePacket({MessageType::ping, 0, 0, 1, 0.1f}); });
}
