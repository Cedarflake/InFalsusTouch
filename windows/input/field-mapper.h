#pragma once

namespace ift {

struct Rect {
  int x = 0;
  int y = 0;
  int width = 1;
  int height = 1;
};

struct Point {
  int x = 0;
  int y = 0;
};

struct FieldConfig {
  double left = 0.05;
  double right = 0.95;
  double y = 0.5;
  double sensitivity = 1;
  double acceleration = 0;
  double smoothing = 0;
  double maxSpeed = 12000;

  void validate() const;
};

Point mapField(double normalizedX, const Rect& client, const FieldConfig& config);
Point normalizeDesktop(Point point, const Rect& desktop);

class RelativeMapper {
public:
  int move(double normalizedDelta, int width, double seconds, const FieldConfig& config);
  void reset();

private:
  double velocity_ = 0;
  double remainder_ = 0;
};

}
