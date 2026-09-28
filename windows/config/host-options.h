#pragma once

#include <cstdint>
#include <string>

#include "windows/input/field-mapper.h"

namespace ift {

struct HostOptions {
  std::uint16_t port = 27184;
  std::uintptr_t window = 0;
  std::wstring title = L"In Falsus";
  std::wstring tracePath;
  FieldConfig field;
  bool dryRun = false;
  bool list = false;
  bool help = false;
};

HostOptions parseOptions(int argc, wchar_t** argv);
void printHelp();

}
