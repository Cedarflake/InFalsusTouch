#include "windows/input/field-mapper.h"

#include <algorithm>
#include <cmath>
#include <stdexcept>

namespace ift {

void FieldConfig::validate() const {
  if (!std::isfinite(left) || !std::isfinite(right) || !std::isfinite(y) ||
      !std::isfinite(sensitivity) || !std::isfinite(acceleration) ||
      !std::isfinite(smoothing) || !std::isfinite(maxSpeed) ||
      left < 0 || right > 1 || left >= right || y < 0 || y > 1 ||
      sensitivity <= 0 || sensitivity > 20 || acceleration < 0 || acceleration > 10 ||
      smoothing < 0 || smoothing >= 1 || maxSpeed <= 0 || maxSpeed > 100000) {
    throw std::invalid_argument("Invalid Field calibration/relative settings");
  }
}

Point mapField(double normalizedX, const Rect& client, const FieldConfig& config) {
  config.validate();
  if (!std::isfinite(normalizedX) || client.width <= 0 || client.height <= 0) {
    throw std::invalid_argument("Invalid Field coordinate/client rectangle");
  }
  const double x = std::clamp(normalizedX, 0.0, 1.0);
  const double ratio = (1 - x) * config.left + x * config.right;
  return {
    client.x + static_cast<int>(std::lround(ratio * (client.width - 1))),
    client.y + static_cast<int>(std::lround(config.y * (client.height - 1))),
  };
}

Point normalizeDesktop(Point point, const Rect& desktop) {
  if (desktop.width <= 1 || desktop.height <= 1) {
    throw std::invalid_argument("Invalid virtual desktop rectangle");
  }
  return {
    static_cast<int>(std::lround(std::clamp(
      static_cast<double>(point.x - desktop.x) / (desktop.width - 1), 0.0, 1.0) * 65535)),
    static_cast<int>(std::lround(std::clamp(
      static_cast<double>(point.y - desktop.y) / (desktop.height - 1), 0.0, 1.0) * 65535)),
  };
}

int RelativeMapper::move(double normalizedDelta, int width, double seconds,
                         const FieldConfig& config) {
  const double dt = std::clamp(seconds, 0.001, 0.05);
  double velocity = normalizedDelta * width * config.sensitivity / dt;
  velocity *= 1 + config.acceleration * std::abs(normalizedDelta);
  velocity = std::clamp(velocity, -config.maxSpeed, config.maxSpeed);
  velocity_ = config.smoothing * velocity_ + (1 - config.smoothing) * velocity;
  const double distance = velocity_ * dt + remainder_;
  const int pixels = static_cast<int>(std::trunc(distance));
  remainder_ = distance - pixels;
  return pixels;
}

void RelativeMapper::reset() {
  velocity_ = 0;
  remainder_ = 0;
}

}
