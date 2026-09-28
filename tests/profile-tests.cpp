#include "tests/test-support.h"
#include "windows/config/host-profile.h"

void profileTests() {
  using namespace ift;
  HostProfile profile;
  profile.field = calibrateField(FieldConfig{}, {-1800, -200, 1001, 501}, {-1745, 0}, {-870, 0}, {-1600, 70});
  profile.field.sensitivity = 1.23456789012345;
  profile.video.width = 1920;
  profile.video.height = 1080;
  profile.video.fps = 30;
  profile.video.bitrate = 12'000'000;
  const auto decoded = parseProfile(serializeProfile(profile));
  check(decoded.field.left == 0.055 && decoded.field.right == 0.93 && decoded.field.y == 0.54,
    "Calibration must use physical client coordinates, including negative desktop origins");
  check(decoded.field.sensitivity == profile.field.sensitivity, "Profile numbers must round-trip without precision loss");
  check(decoded.video.height == 1080 && decoded.video.fps == 30 && decoded.video.bitrate == 12'000'000,
    "Video options must survive profile round-trip");
  check(parseProfile("# profile\r\nversion = 1\r\nfield-left = 0.1\r\n").field.left == 0.1, "CRLF profile parsing failed");
  for (const auto text : {"", "version=2", "version=1\nversion=1", "version=1\nunknown=0",
       "version=1\nfps=65560", "version=1\nfps=nan", "version=1\nfield-left=nan", "version=1\nfield-left=0.99",
       "version=1\nbitrate=-1", "version=1\nresolution=4k", "version=1\nsmoothing=1", "version=1\nfps=30junk"}) {
    expectError<std::invalid_argument>([&] { parseProfile(text); });
  }
  expectError<std::invalid_argument>([] { parseProfile(std::string(8193, ' ')); });
  expectError<std::invalid_argument>([] {
    calibrateField({}, {0, 0, 100, 100}, {-1, 5}, {90, 5}, {50, 50});
  });
  expectError<std::invalid_argument>([] {
    calibrateField({}, {0, 0, 100, 100}, {90, 5}, {10, 5}, {50, 50});
  });
}
