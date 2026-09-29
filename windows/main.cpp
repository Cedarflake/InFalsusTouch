#include <Windows.h>

#include <atomic>
#include <clocale>
#include <iostream>
#include <memory>
#include <thread>

#include "windows/config/host-options.h"
#include "windows/config/field-calibration.h"
#include "windows/config/profile-file.h"
#include "windows/input/trace-input.h"
#include "windows/input/win32-input.h"
#include "windows/transport/control-server.h"
#include "windows/transport/usb-bridge.h"
#include "windows/video/media-runtime.h"
#include "windows/transport/video-server.h"

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
  std::setlocale(LC_CTYPE, ".UTF8");
  SetConsoleOutputCP(CP_UTF8);
  DWORD consoleMode = 0;
  const auto consoleInput = GetStdHandle(STD_INPUT_HANDLE);
  if (GetConsoleMode(consoleInput, &consoleMode)) {
    SetConsoleMode(consoleInput, (consoleMode | ENABLE_EXTENDED_FLAGS) & ~ENABLE_QUICK_EDIT_MODE);
  }
  int result = 0;
  try {
    auto options = ift::parseOptions(argc, argv);
    if (options.help) {
      ift::printHelp();
      return 0;
    }
    if (options.list) {
      ift::printWindows(ift::listWindows());
      return 0;
    }
    if (options.videoDiagnostics) {
      ift::printVideoDiagnostics();
      return 0;
    }
    if (options.calibrate || options.saveProfile) {
      if (options.calibrate) options.field = ift::calibrateGameWindow(ift::chooseWindow(options), options.field);
      ift::writeProfileFile(options.profilePath, {options.field, options.video});
      std::wcout << L"Saved profile: " << options.profilePath << L"\nStart Host again to use these settings.\n";
      return 0;
    }
    ift::checkControlPort(options.port);
    const auto window = options.dryRun && !options.video.enabled ? nullptr : ift::chooseWindow(options);
    if (window && !options.dryRun) ift::printTargetKeyboard(window);
    ift::GameWindow target(options.dryRun ? nullptr : window);
    std::unique_ptr<ift::InputSink> sink;
    if (options.dryRun) {
      sink = std::make_unique<ift::TraceInput>(options.tracePath);
    } else {
      sink = std::make_unique<ift::Win32Input>(window);
    }
    shutdownComplete = CreateEventW(nullptr, TRUE, FALSE, nullptr);
    if (!shutdownComplete || !SetConsoleCtrlHandler(handleConsoleSignal, TRUE)) {
      throw std::runtime_error("Could not register clean shutdown handler");
    }
    std::jthread video;
    if (options.video.enabled) {
      video = std::jthread([&](std::stop_token token) {
        ift::runVideoServer(options.video, window, stopping, token);
      });
    }
    std::jthread usb;
    if (options.usb) usb = std::jthread([&](std::stop_token token) { ift::maintainUsb(options, stopping, token); });
    ift::runControlServer(options, target, *sink, stopping);
  } catch (const winrt::hresult_error& error) {
    std::wcerr << L"InFalsusTouchHost: " << error.message().c_str() << L" (0x"
      << std::hex << static_cast<unsigned long>(error.code()) << L")\n";
    result = 1;
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
