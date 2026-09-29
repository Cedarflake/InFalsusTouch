#include "tests/test-support.h"
#include "protocol/cpp/video-packet.h"
#include "windows/video/frame-pacer.h"


void videoTests() {
  using namespace ift::video;
  const RequestBytes phoneRequest{'I', 'F', 'V', '1', 2, 0, 0, 120};
  check(encodeRequest(120) == phoneRequest, "Video subscription wire format changed");
  for (const auto fps : {0, 24, 30, 60, 90, 120}) {
    check(decodeRequest(encodeRequest(static_cast<std::uint16_t>(fps))) == fps, "Subscription FPS did not round trip");
  }
  for (std::size_t size = 0; size < requestSize; ++size) {
    expectError<std::invalid_argument>([&] { decodeRequest(std::span(phoneRequest).first(size)); });
  }
  for (std::size_t offset : {0u, 4u, 5u, 6u, 7u}) {
    auto bad = phoneRequest;
    bad[offset] = 255;
    expectError<std::invalid_argument>([&] { decodeRequest(bad); });
  }
  for (const auto fps : {23, 121, 65535}) {
    expectError<std::invalid_argument>([&] { encodeRequest(static_cast<std::uint16_t>(fps)); });
  }
  {
    ift::FramePacer pacing(120);
    constexpr std::uint64_t start = 1'000'000'000;
    auto nextSource = start;
    bool available = false;
    std::vector<std::uint64_t> submissions;
    for (auto now = start; now < start + 1'000'000'000; now += 1'000'000) {
      if (now >= nextSource) {
        available = true;
        nextSource += 1'000'000'000 / 165;
      }
      if (available && pacing.ready(now)) {
        submissions.push_back(now);
        pacing.submitted(now);
        available = false;
      }
    }
    check(submissions.size() == 120, "Source refresh changed the requested output rate");
    for (std::size_t index = 1; index < submissions.size(); ++index) {
      const auto interval = submissions[index] - submissions[index - 1];
      check(interval >= 8'000'000 && interval <= 9'000'000, "Source arrivals leaked into the output cadence");
    }
    const auto resumed = start + 2'000'000'000;
    check(pacing.ready(resumed), "Pacing failed to resume after a stall");
    pacing.submitted(resumed);
    check(!pacing.ready(resumed + 1'000'000), "Pacing replayed missed frames in a burst");
    check(pacing.ready(resumed + 8'333'333), "Pacing failed to establish a new deadline");
    for (const auto fps : {0, 23, 121}) {
      expectError<std::invalid_argument>([&] { ift::FramePacer invalid(static_cast<std::uint16_t>(fps)); });
    }
  }
  Header header;
  header.keyFrame = true;
  header.payloadSize = 512;
  header.sequence = 123;
  header.captureTimestamp = 1'000'000;
  header.encodeTimestamp = 2'000'000;
  header.sendTimestamp = 3'000'000;
  header.presentationUs = 1000;
  const auto good = encodeHeader(header);
  auto legacy = good;
  legacy[4] = 1;
  expectError<std::invalid_argument>([&] { decodeHeader(legacy); });
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
