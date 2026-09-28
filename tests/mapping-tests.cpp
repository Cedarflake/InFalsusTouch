#include "tests/test-support.h"

#include <limits>

void mappingTests() {
  using namespace ift;
  const Rect client{-1800, -200, 1001, 501};
  const FieldConfig config;
  check(mapField(0, client, config).x == -1750, "Field left mapping failed");
  check(mapField(0.5, client, config).x == -1300, "Field center mapping failed");
  check(mapField(1, client, config).x == -850, "Field right mapping failed");
  check(mapField(0.5, client, config).y == 50, "Fixed Y mapping failed");
  check(mapField(-10, client, config).x == -1750, "Field clamp failed");
  check(mapField(0.5, {0, 0, 1280, 720}, config).x == 640,
        "Even-sized window center must round to the nearest pixel consistently");
  const Rect desktop{-1920, -1080, 3840, 2160};
  check(normalizeDesktop({-1920, -1080}, desktop).x == 0, "Negative desktop origin failed");
  const auto edge = normalizeDesktop({1919, 1079}, desktop);
  check(edge.x == 65535 && edge.y == 65535, "Desktop endpoint mapping failed");
  expectError<std::invalid_argument>([] { FieldConfig{0.8, 0.2}.validate(); });
  expectError<std::invalid_argument>([] { FieldConfig{0, 1, 2}.validate(); });
  expectError<std::invalid_argument>([&] {
    mapField(std::numeric_limits<double>::quiet_NaN(), client, config);
  });
  RelativeMapper relative;
  check(relative.move(0.1, 1000, 0.01, config) == 100, "Relative displacement failed");
  relative.reset();
  auto limited = config;
  limited.maxSpeed = 100;
  check(relative.move(1, 1000, 0.01, limited) == 1, "Relative speed cap failed");
}
