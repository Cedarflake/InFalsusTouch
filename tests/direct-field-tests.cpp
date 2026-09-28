#include "tests/test-support.h"
#include "windows/input/direct-field.h"

#include <cmath>
#include <limits>

void directFieldTests() {
  using namespace ift;
  using namespace std::chrono_literals;
  const auto now = DirectField::Clock::now();
  DirectField direct;
  direct.begin();
  check(direct.target(0.25), "Direct touch target rejected");
  FieldFeedback feedback{1, 0.75, 1.8};
  const auto first = direct.update(feedback, 2560, now);
  check(first && *first == -711, "First touch must jump from the actual game position");
  direct.target(0.9);
  check(!direct.update(feedback, 2560, now + 2ms), "Unconsumed input must not be injected twice");
  direct.target(0.1);
  feedback.position += *first * feedback.sensitivity / 2560;
  const auto latest = direct.update(feedback, 2560, now + 8ms);
  check(latest && *latest < 0, "Rapid changes must use only the latest target after feedback");
  feedback.position += *latest * feedback.sensitivity / 2560;
  check(!direct.update(feedback, 2560, now + 16ms), "Settled touch must not jitter");
  check(std::abs(feedback.position - 0.1) <= feedback.sensitivity / 2560, "Direct target is inaccurate");

  direct.finish();
  direct.update(feedback, 2560, now + 20ms);
  check(!direct.pending(), "A completed gesture must stop polling");
  feedback.position = -2.5;
  feedback.sensitivity = 0.5;
  direct.begin();
  direct.target(0.75);
  const auto fromOutside = direct.update(feedback, 2560, now + 24ms);
  check(fromOutside == 16640, "Offscreen accumulated position must be corrected without homing");
  feedback.position += *fromOutside * feedback.sensitivity / 2560;
  direct.update(feedback, 2560, now + 32ms);
  feedback.sensitivity = 3;
  direct.target(0.25);
  const auto changedSensitivity = direct.update(feedback, 2560, now + 40ms);
  check(changedSensitivity == -427, "Game sensitivity changes must take effect without reconnecting");

  direct.reset();
  feedback = {2, 0.5, 1.2};
  direct.begin();
  direct.target(0.6);
  const auto motion = direct.update(feedback, 1920, now);
  direct.target(0.8);
  direct.finish();
  check(!direct.update(feedback, 1920, now + 1ms), "Lift must not duplicate pending motion");
  feedback.position += *motion * feedback.sensitivity / 1920;
  const auto finalMotion = direct.update(feedback, 1920, now + 8ms);
  check(finalMotion == 320, "The final touch sample must complete after lift");
  feedback.position += *finalMotion * feedback.sensitivity / 1920;
  direct.update(feedback, 1920, now + 16ms);
  check(!direct.pending() && std::abs(feedback.position - 0.8) < 0.00001,
        "Lift must leave Field at the final touch position");

  direct.begin();
  direct.target(0.2);
  const auto beforeRetouch = direct.update(feedback, 1920, now + 20ms);
  direct.cancel();
  direct.begin();
  direct.target(0.9);
  check(!direct.update(feedback, 1920, now + 21ms), "Immediate re-touch must account for in-flight motion");
  feedback.position += *beforeRetouch * feedback.sensitivity / 1920;
  const auto retouch = direct.update(feedback, 1920, now + 28ms);
  check(retouch == 1120, "Re-touch must use the new finger position");
  direct.cancel();
  feedback.position += *retouch * feedback.sensitivity / 1920;
  check(!direct.update(feedback, 1920, now + 36ms) && !direct.pending(),
        "Disconnect must never generate a final correction");

  direct.begin();
  direct.target(0.1);
  check(direct.update(feedback, 1920, now + 40ms).has_value(), "Stall setup failed");
  check(!direct.update(feedback, 1920, now + 141ms) && direct.blocked(),
        "Ignored input must stop correction without building up an overshoot");
  check(!direct.target(0.8), "A stalled gesture needs a new finger down");
  direct.begin();
  check(direct.target(0.4), "A new gesture must recover from a stalled game");
  feedback.identity = 3;
  check(direct.update(feedback, 1920, now + 150ms).has_value(), "New game state must discard stale pending input");
  direct.reset();
  check(!direct.pending() && !direct.blocked(), "Focus loss must discard all direct-control state");
  direct.begin();
  direct.target(0.4);
  direct.block();
  check(direct.blocked() && !direct.pending() && !direct.target(0.7),
        "Failed injection must reject further movement until a fresh touch");
  direct.begin();
  check(direct.target(0.7), "Fresh touch must recover from an injection failure");
  direct.reset();
  check(!direct.target(std::numeric_limits<double>::quiet_NaN()), "NaN target must fail closed");
  direct.target(0.5);
  for (const double invalid : {0.0, -1.0, std::numeric_limits<double>::infinity()}) {
    check(!direct.update({4, 0.25, invalid}, 1920, now), "Invalid sensitivity must never inject input");
  }
}
