#include "tests/test-support.h"
#include "windows/target/game-window.h"

#include <iostream>
#include <sstream>

int main() {
  try {
    const auto game = reinterpret_cast<HWND>(1);
    const auto browser = reinterpret_cast<HWND>(2);
    const auto other = reinterpret_cast<HWND>(3);
    const std::vector<ift::WindowInfo> windows{
      {browser, L"In Falsus 浏览器页面", false},
      {other, L"In Falsus", false},
      {game, L"In Falsus", true},
    };
    const auto selected = ift::matchingWindows(windows, L"In Falsus");
    check(selected.size() == 1 && selected.front().handle == game, "Browser title must not shadow the game");
    check(ift::matchingWindows({windows[0], windows[1]}, L"In Falsus").empty(),
      "A browser alone must not become the default game target");
    const auto renamed = ift::matchingWindows({{game, L"In Falsus 1.0.4b", true}}, L"IN FALSUS");
    check(renamed.size() == 1 && renamed.front().handle == game, "Game process detection must tolerate title changes");
    const auto custom = ift::matchingWindows({{game, L"Test", false}, {browser, L"Test documentation", false}}, L"test");
    check(custom.size() == 1 && custom.front().handle == game, "Exact custom titles must take priority");
    check(ift::matchingWindows({{game, L"In Falsus", true}, {other, L"In Falsus", true}}, L"In Falsus").size() == 2,
      "Multiple game windows must still require a choice");
    std::ostringstream output;
    const auto previous = std::cout.rdbuf(output.rdbuf());
    ift::printWindows(windows);
    std::cout.rdbuf(previous);
    check(output.str().find("浏览器页面") != std::string::npos, "Window titles must retain UTF-8 text");
    check(output.str().find("3. 0x1  In Falsus\n") != std::string::npos,
      "A Unicode title must not truncate subsequent windows");
    std::cout << "Window selection tests passed\n";
    return 0;
  } catch (const std::exception& error) {
    std::cerr << error.what() << '\n';
    return 1;
  }
}
