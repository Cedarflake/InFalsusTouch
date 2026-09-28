#include "tests/test-support.h"

void inputTests() {
  using namespace ift;
  RecordingSink sink;
  {
    InputState state(sink, {});
    state.updateTarget(true, {100, 200, 1001, 501});
    InputSession session(state);
    expectError<ProtocolError>([&] { session.process({MessageType::laneDown, 1}, 0.01); });
    check(session.process({MessageType::hello}, 0.01).status == 1, "Handshake needs barrier");
    expectError<ProtocolError>([&] { session.process({MessageType::hello, 0, 0, 2}, 0.01); });
    check(session.process({MessageType::releaseAll, 0, 0, 2}, 0.01).status == 0, "Barrier did not arm");
    std::uint32_t sequence = 3;
    for (std::uint8_t lane = 1; lane <= 6; ++lane) {
      session.process({MessageType::laneDown, lane, 0, sequence++}, 0.01);
    }
    check(state.pressedMask() == 63, "Six-key chord failed");
    session.process({MessageType::laneDown, 3, 0, sequence++}, 0.01);
    check(sink.keys.size() == 6, "Duplicate down must be idempotent");
    session.process({MessageType::fieldAbsolute, 0, 0, sequence++, 0.5f}, 0.01);
    check(sink.point.x == 600 && sink.point.y == 450, "Field/chord interference");
    session.process({MessageType::laneUp, 3, 0, sequence++}, 0.01);
    check(state.pressedMask() == 59, "Independent lane release failed");
    state.updateTarget(false, {});
    check(state.pressedMask() == 0, "Focus loss must release all keys");
    state.updateTarget(true, {0, 0, 100, 100});
    check(session.process({MessageType::laneDown, 1, 0, sequence++}, 0.01).status == 1,
          "Focus return must not replay stale input");
    session.process({MessageType::releaseAll, 0, 0, sequence++}, 0.01);
    session.process({MessageType::laneDown, 6, 0, sequence++}, 0.01);
    expectError<ProtocolError>([&] { session.process({MessageType::ping, 0, 0, sequence + 1}, 0.01); });
    check(state.pressedMask() == 32, "Hold lost before session destruction");
  }
  check(sink.keys.back() == -6, "Disconnect/destructor must release held key");
  {
    InputState reconnected(sink, {});
    InputSession session(reconnected);
    session.process({MessageType::hello}, 0.01);
    check(reconnected.pressedMask() == 0, "Reconnect must start empty");
  }
  {
    InputState state(sink, {});
    state.updateTarget(true, {0, 0, 100, 100});
    state.apply({MessageType::releaseAll}, 0.01);
    sink.fail = true;
    expectError<std::runtime_error>([&] { state.apply({MessageType::laneDown, 1}, 0.01); });
    check(state.pressedMask() == 1, "Failed DOWN must remain tracked for cleanup");
    check(!state.releaseAll(), "Release failure must not be hidden");
    sink.fail = false;
    check(state.releaseAll() && state.pressedMask() == 0, "Failed release must be retryable");
  }
}
