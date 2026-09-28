#include "tests/test-support.h"

#include <sstream>

#include "windows/config/key-bindings.h"
#include "windows/input/control-group.h"
#include "windows/input/input-mixer.h"

void coopTests() {
  using namespace ift;
  RecordingSink sink;
  InputMixer mixer(sink);
  ControllerInput a(mixer, 0), b(mixer, 1);
  InputState first(a, {}), second(b, {});
  for (auto* state : {&first, &second}) {
    state->updateTarget(true, {0, 0, 1000, 500});
    state->apply({MessageType::releaseAll}, 0.01);
    state->apply({MessageType::laneDown, 2}, 0.01);
  }
  check(sink.keys == std::vector<int>{2}, "Overlapping devices must inject one DOWN");
  first.releaseAll();
  check(sink.keys.size() == 1, "One disconnect must preserve the other device hold");
  second.releaseAll();
  check(sink.keys == std::vector<int>({2, -2}), "Last holder must release the key");
  first.apply({MessageType::fieldBegin}, 0.01);
  second.apply({MessageType::fieldBegin}, 0.01);
  first.apply({MessageType::fieldAbsolute, 0, 0, 1, 0.2f}, 0.01);
  const auto ownedPosition = sink.point.x;
  second.apply({MessageType::fieldAbsolute, 0, 0, 1, 0.8f}, 0.01);
  check(sink.point.x == ownedPosition && b.fieldStatus() == 2, "Waiting Field must not move the cursor");
  first.apply({MessageType::fieldEnd}, 0.01);
  second.apply({MessageType::fieldAbsolute, 0, 0, 1, 0.8f}, 0.01);
  check(sink.point.x > ownedPosition && b.fieldStatus() == 4, "Field ownership must pass after release");
  second.setControls(1);
  second.apply({MessageType::releaseAll}, 0.01);
  const auto before = sink.keys.size();
  second.apply({MessageType::laneDown, 2}, 0.01);
  second.apply({MessageType::fieldBegin}, 0.01);
  check(sink.keys.size() == before && !mixer.ownsField(1), "Hidden controls must not inject input");
  ControlGroup group;
  for (std::size_t index = 0; index < maxControllers; ++index) group.join(index);
  check(group.size() == 7, "Seven controllers must be accepted");
  check(group.assign(0, 64) && group.assign(1, 64), "Repeated visible Field must be allowed");
  check(group.controls(0) == 64 && group.controls(1) == 64, "Choosing controls must not change another phone");
  check(group.assign(2, 0) && group.size() == 7, "View-only device still counts as connected");
  group.leave(0);
  check(group.size() == 6 && group.controls(1) == 64, "Disconnect must preserve other display choices");
  expectError<std::logic_error>([&] { group.join(7); });

  std::ostringstream preferences;
  for (std::size_t index = 0; index < defaultBindings.size(); ++index) {
    preferences << "k \"keybind_BottomLane" << index << "\"\nv " << static_cast<int>(defaultBindings[index])
      << "\nk \"keybind_state_BottomLane" << index << "\"\nv 0\n";
  }
  const auto valid = preferences.str();
  check(parseGameBindings(valid) == defaultBindings, "IF preferences must map to default keys");
  check(keyScanCode(51) == 0x2a && keyScanCode(15) == 0x1e && keyScanCode(33) == 0x1f &&
    keyScanCode(18) == 0x20 && keyScanCode(20) == 0x21 && keyScanCode(1) == 0x39, "Default physical scan codes changed");
  check(keyScanCode(56) == 0xe01d && keyScanCode(77) == 0xe01c, "Extended keys need E0 scans");
  check(keyScanCode(76) == 0 && keyScanCode(106) == 0, "Unknown physical mappings must fail closed");
  check(packBindings(defaultBindings) == 0x330f21121401ULL, "Binding wire order changed");
  expectError<std::invalid_argument>([&] { parseGameBindings(valid.substr(0, valid.size() - 4)); });
  expectError<std::invalid_argument>([&] { parseGameBindings(valid + "k \"keybind_BottomLane1\"\nv 16\n"); });
  auto unsupported = valid;
  unsupported.replace(unsupported.find("v 51"), 4, "v 111");
  expectError<std::invalid_argument>([&] { parseGameBindings(unsupported); });
}
