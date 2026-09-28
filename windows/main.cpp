#include <Windows.h>

#include <atomic>
#include <iostream>
#include <memory>

#include "windows/config/host-options.h"
#include "windows/input/trace-input.h"
#include "windows/input/win32-input.h"
#include "windows/transport/control-server.h"

namespace {

std::atomic_bool stopping{false};
HANDLE shutdownComplete = nullptr;

BOOL WINAPI handleConsoleSignal(DWORD signal) {
  if (signal == CTRL_C_EVENT || signal == CTRL_BREAK_EVENT || signal == CTRL_CLOSE_EVENT ||
      signal == CTRL_LOGOFF_EVENT || signal == CTRL_SHUTDOWN_EVENT) {
    stopping.store(true);
    if (signal != CTRL_C_EVENT && signal != CTRL_BREAK_EVENT && shutdownComplete) {
      WaitForSingleObject(shutdownComplete, 3000);
    }
    return TRUE;
  }
  return FALSE;
}

}

int wmain(int argc, wchar_t** argv) {
  int result = 0;
  try {
    const auto options = ift::parseOptions(argc, argv);
    if (options.help) {
      ift::printHelp();
      return 0;
    }
    if (options.list) {
      ift::printWindows(ift::listWindows());
      return 0;
    }
    ift::GameWindow target(options.dryRun ? nullptr : ift::chooseWindow(options));
    std::unique_ptr<ift::InputSink> sink;
    if (options.dryRun) {
      sink = std::make_unique<ift::TraceInput>(options.tracePath);
    } else {
      sink = std::make_unique<ift::Win32Input>();
    }
    shutdownComplete = CreateEventW(nullptr, TRUE, FALSE, nullptr);
    if (!shutdownComplete || !SetConsoleCtrlHandler(handleConsoleSignal, TRUE)) {
      throw std::runtime_error("Could not register clean shutdown handler");
    }
    ift::runControlServer(options, target, *sink, stopping);
  } catch (const std::exception& error) {
    std::cerr << "InFalsusTouchHost: " << error.what() << '\n';
    result = 1;
  }
  if (shutdownComplete) {
    SetEvent(shutdownComplete);
    SetConsoleCtrlHandler(handleConsoleSignal, FALSE);
    CloseHandle(shutdownComplete);
    shutdownComplete = nullptr;
  }
  return result;
}
