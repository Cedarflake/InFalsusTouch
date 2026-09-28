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

  void validate() const;
};

Point mapField(double normalizedX, const Rect& client, const FieldConfig& config);
Point normalizeDesktop(Point point, const Rect& desktop);
FieldConfig calibrateField(const FieldConfig& base, const Rect& client, Point left, Point right, Point vertical);

class RelativeMapper {
public:
  int move(double normalizedDelta);
  void reset();

private:
  double remainder_ = 0;
};

}
