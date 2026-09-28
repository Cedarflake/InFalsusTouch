#include "windows/config/host-options.h"

#include <iostream>
#include <stdexcept>

namespace ift {
namespace {

double number(const std::wstring& text) {
  std::size_t consumed = 0;
  const double value = std::stod(text, &consumed);
  if (consumed != text.size()) {
    throw std::invalid_argument("Invalid numeric option");
  }
  return value;
}

std::uint64_t integer(const std::wstring& text) {
  if (text.empty() || text.front() == L'-') {
    throw std::invalid_argument("Expected an unsigned integer");
  }
  std::size_t consumed = 0;
  const auto value = std::stoull(text, &consumed, 0);
  if (consumed != text.size()) {
    throw std::invalid_argument("Invalid integer option");
  }
  return value;
}

}

HostOptions parseOptions(int argc, wchar_t** argv) {
  HostOptions options;
  for (int index = 1; index < argc; ++index) {
    const std::wstring option = argv[index];
    if (option == L"--dry-run") {
      options.dryRun = true;
      continue;
    }
    if (option == L"--list") {
      options.list = true;
      continue;
    }
    if (option == L"--help" || option == L"-h") {
      options.help = true;
      continue;
    }
    if (++index >= argc) {
      throw std::invalid_argument("Missing option value; use --help");
    }
    const std::wstring value = argv[index];
    if (option == L"--port") {
      const auto port = integer(value);
      if (port < 1024 || port > 65535) {
        throw std::invalid_argument("Port must be in [1024, 65535]");
      }
      options.port = static_cast<std::uint16_t>(port);
    } else if (option == L"--window") {
      options.window = static_cast<std::uintptr_t>(integer(value));
    } else if (option == L"--title") {
      options.title = value;
    } else if (option == L"--trace") {
      options.tracePath = value;
    } else if (option == L"--field-left") {
      options.field.left = number(value);
    } else if (option == L"--field-right") {
      options.field.right = number(value);
    } else if (option == L"--field-y") {
      options.field.y = number(value);
    } else if (option == L"--sensitivity") {
      options.field.sensitivity = number(value);
    } else if (option == L"--acceleration") {
      options.field.acceleration = number(value);
    } else if (option == L"--smoothing") {
      options.field.smoothing = number(value);
    } else if (option == L"--max-speed") {
      options.field.maxSpeed = number(value);
    } else {
      throw std::invalid_argument("Unknown option; use --help");
    }
  }
  options.field.validate();
  if (!options.tracePath.empty() && !options.dryRun) {
    throw std::invalid_argument("--trace is only supported with --dry-run");
  }
  return options;
}

void printHelp() {
  std::cout << "InFalsusTouchHost - USB input prototype\n"
    "  --list                  List selectable windows and handles\n"
    "  --window 0xHANDLE       Select an exact window\n"
    "  --title TEXT            Match title (default: In Falsus)\n"
    "  --port 27184            Control listener on 127.0.0.1 only\n"
    "  --field-left 0.05 --field-right 0.95 --field-y 0.5\n"
    "  --sensitivity 1 --acceleration 0 --smoothing 0 --max-speed 12000\n"
    "  --dry-run [--trace PATH] Simulate input without calling SendInput\n"
    "Focus the selected game before touching. Ctrl+C releases keys and exits.\n";
}

}
