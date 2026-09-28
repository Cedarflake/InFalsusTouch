#include "tests/test-support.h"
#include "protocol/cpp/video-packet.h"

void videoTests() {
  using namespace ift::video;
  Header header;
  header.keyFrame = true;
  header.payloadSize = 512;
  header.sequence = 123;
  header.captureTimestamp = 1'000'000;
  header.encodeTimestamp = 2'000'000;
  header.sendTimestamp = 3'000'000;
  header.presentationUs = 1000;
  const auto good = encodeHeader(header);
  const auto decoded = decodeHeader(good);
  check(decoded.captureTimestamp == header.captureTimestamp && decoded.sequence == 123 && decoded.keyFrame,
    "Video header endian mismatch");
  check(encodeHeader(decoded) == good, "Video header roundtrip mismatch");
  for (const auto fps : {24, 60, 90, 120}) {
    auto highRate = header;
    highRate.fps = static_cast<std::uint16_t>(fps);
    check(decodeHeader(encodeHeader(highRate)).fps == fps, "Supported video rate did not round-trip");
  }
  for (const auto fps : {0, 23, 121, 65535}) {
    auto invalidRate = header;
    invalidRate.fps = static_cast<std::uint16_t>(fps);
    expectError<std::invalid_argument>([&] { encodeHeader(invalidRate); });
  }
  for (std::size_t size = 0; size < headerSize; ++size) {
    expectError<std::invalid_argument>([&] { decodeHeader(std::span(good).first(size)); });
  }
  for (std::size_t offset : {0u, 4u, 5u, 6u, 7u, 8u, 48u, 50u, 52u, 54u, 60u}) {
    auto bad = good;
    bad[offset] = 255;
    expectError<std::invalid_argument>([&] { decodeHeader(bad); });
  }
  header.encodeTimestamp = 1;
  expectError<std::invalid_argument>([&] { encodeHeader(header); });
  const std::vector<std::uint8_t> annexB{0, 0, 0, 1, 0x67, 0x42, 0, 0, 1, 0x68, 0x12, 0, 0, 1, 0x65, 0x45};
  check(toAnnexB(annexB) == annexB && hasNal(annexB, 5), "Annex B IDR missing");
  const auto parameters = extractParameterSets(annexB);
  check(hasNal(parameters, 7) && hasNal(parameters, 8) && !hasNal(parameters, 5), "Wrong parameter sets");
  const std::vector<std::uint8_t> avcc{1, 66, 0, 40, 255, 225, 0, 2, 0x67, 0x42, 1, 0, 2, 0x68, 0x12};
  check(toAnnexB(avcc) == parameters, "AVC configuration conversion failed");
  const std::vector<std::uint8_t> lengthPrefixed{0, 0, 0, 2, 0x65, 0x45};
  check(hasNal(toAnnexB(lengthPrefixed), 5), "AVC access unit conversion failed");
  for (const auto& bad : std::vector<std::vector<std::uint8_t>>{
      {}, {0, 0}, {0, 0, 0, 1}, {0, 0, 0, 16, 0x65}, {0, 0, 1, 0xFF}}) {
    expectError<std::invalid_argument>([&] { toAnnexB(bad); });
  }
}
