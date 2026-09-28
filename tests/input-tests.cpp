#include "tests/test-support.h"

void inputTests() {
  using namespace ift;
  RecordingSink sink;
  {
    InputState state(sink, {});
    state.updateTarget(true, {100, 200, 1001, 501});
    InputSession session(state);
    expectError<ProtocolError>([&] { session.process({MessageType::laneDown, 1}); });
    check(session.process({MessageType::hello}).status == 1, "Handshake needs barrier");
    expectError<ProtocolError>([&] { session.process({MessageType::hello, 0, 0, 2}); });
    check(session.process({MessageType::releaseAll, 0, 0, 2}).status == 0, "Barrier did not arm");
    std::uint32_t sequence = 3;
    for (std::uint8_t lane = 1; lane <= 6; ++lane) {
      session.process({MessageType::laneDown, lane, 0, sequence++});
    }
    check(state.pressedMask() == 63, "Six-key chord failed");
    session.process({MessageType::laneDown, 3, 0, sequence++});
    check(sink.keys.size() == 6, "Duplicate down must be idempotent");
    session.process({MessageType::fieldAbsolute, 0, 0, sequence++, 0.5f});
    check(sink.point.x == 600 && sink.point.y == 450, "Field/chord interference");
    session.process({MessageType::laneUp, 3, 0, sequence++});
    check(state.pressedMask() == 59, "Independent lane release failed");
    state.updateTarget(false, {});
    check(state.pressedMask() == 0, "Focus loss must release all keys");
    state.updateTarget(true, {0, 0, 100, 100});
    check(session.process({MessageType::laneDown, 1, 0, sequence++}).status == 1,
          "Focus return must not replay stale input");
    session.process({MessageType::releaseAll, 0, 0, sequence++});
    session.process({MessageType::laneDown, 6, 0, sequence++});
    expectError<ProtocolError>([&] { session.process({MessageType::ping, 0, 0, sequence + 1}); });
    check(state.pressedMask() == 32, "Hold lost before session destruction");
  }
  check(sink.keys.back() == -6, "Disconnect/destructor must release held key");
  {
    InputState reconnected(sink, {});
    InputSession session(reconnected);
    session.process({MessageType::hello});
    check(reconnected.pressedMask() == 0, "Reconnect must start empty");
  }
  {
    InputState state(sink, {});
    state.updateTarget(true, {0, 0, 100, 100});
    state.apply({MessageType::releaseAll});
    sink.fail = true;
    expectError<std::runtime_error>([&] { state.apply({MessageType::laneDown, 1}); });
    check(state.pressedMask() == 1, "Failed DOWN must remain tracked for cleanup");
    check(!state.releaseAll(), "Release failure must not be hidden");
    sink.fail = false;
    check(state.releaseAll() && state.pressedMask() == 0, "Failed release must be retryable");
  }
  {
    RecordingSink relativeSink;
    InputState state(relativeSink, {0.2, 0.8, 0.3});
    state.updateTarget(true, {0, 0, 640, 360});
    state.apply({MessageType::releaseAll});
    state.apply({MessageType::fieldBegin});
    state.apply({MessageType::fieldRelative, 0, 0, 1, 0.25f});
    check(relativeSink.relativeX == 320, "Relative input must use a fixed reference, not absolute calibration");
    state.updateTarget(true, {-1920, 100, 3840, 2160});
    state.apply({MessageType::fieldRelative, 0, 0, 2, 0.25f});
    check(relativeSink.relativeX == 640, "Resizing or moving the game must not change Host relative gain");
    state.apply({MessageType::fieldRelative, 0, 0, 3, 3.0f / 4096});
    state.apply({MessageType::fieldEnd});
    state.apply({MessageType::fieldBegin});
    state.apply({MessageType::fieldRelative, 0, 0, 4, 1.0f / 4096});
    check(relativeSink.relativeX == 640, "Retouch must discard the previous gesture's fractional movement");
    state.updateTarget(false, {});
    state.apply({MessageType::fieldRelative, 0, 0, 5, 1});
    state.updateTarget(true, {0, 0, 1280, 720});
    state.apply({MessageType::fieldRelative, 0, 0, 6, 1});
    check(relativeSink.relativeX == 640, "Inactive or stale relative motion must not reach the sink");
    state.apply({MessageType::releaseAll});
    state.apply({MessageType::fieldBegin});
    state.apply({MessageType::fieldRelative, 0, 0, 7, 1});
    check(relativeSink.relativeX == 1920, "Rearmed input must preserve a fast full-width swipe");
  }
}
