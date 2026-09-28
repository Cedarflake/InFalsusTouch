#include "tests/test-support.h"
#include "windows/config/host-profile.h"

void profileTests() {
  using namespace ift;
  HostProfile profile;
  profile.field = calibrateField(FieldConfig{}, {-1800, -200, 1001, 501}, {-1745, 0}, {-870, 0}, {-1600, 70});
  profile.video.width = 1920;
  profile.video.height = 1080;
  profile.video.fps = 30;
  profile.video.bitrate = 12'000'000;
  const auto decoded = parseProfile(serializeProfile(profile));
  check(decoded.field.left == 0.055 && decoded.field.right == 0.93 && decoded.field.y == 0.54,
    "Calibration must use physical client coordinates, including negative desktop origins");
  check(decoded.video.height == 1080 && decoded.video.fps == 30 && decoded.video.bitrate == 12'000'000,
    "Video options must survive profile round-trip");
  check(parseProfile("# profile\r\nversion = 1\r\nfield-left = 0.1\r\n").field.left == 0.1, "CRLF profile parsing failed");
  profile.field.left = 0.123456789012345;
  check(parseProfile(serializeProfile(profile)).field.left == profile.field.left, "Profile numbers must round-trip without precision loss");
  const auto legacy = parseProfile("sensitivity=2\nacceleration=3\nsmoothing=0.9\nmax-speed=100\nversion=1\nfield-left=0.12\nresolution=1080p\nfps=30\n");
  check(legacy.hasLegacyRelativeSettings && legacy.field.left == 0.12 && legacy.video.height == 1080 && legacy.video.fps == 30,
    "Legacy migration must preserve calibration and video while retiring relative tuning");
  const auto migrated = serializeProfile(legacy);
  check(migrated.starts_with("version=2\n") && migrated.find("sensitivity") == std::string::npos &&
    migrated.find("acceleration") == std::string::npos && migrated.find("smoothing") == std::string::npos &&
    migrated.find("max-speed") == std::string::npos && !parseProfile(migrated).hasLegacyRelativeSettings,
    "Migrated profiles must not retain hidden relative tuning");
  for (const auto text : {"", "version=3", "version=1\nversion=1", "version=1\nunknown=0",
       "version=1\nfps=65560", "version=1\nfps=nan", "version=1\nfield-left=nan", "version=1\nfield-left=0.99",
       "version=1\nbitrate=-1", "version=1\nresolution=4k", "version=1\nsmoothing=1", "version=1\nfps=30junk",
       "version=1\nsensitivity=nan", "version=2\nsensitivity=2", "smoothing=0.5\nversion=2"}) {
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
