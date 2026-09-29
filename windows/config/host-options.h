#pragma once

#include <cstdint>
#include <string>

#include "windows/input/field-mapper.h"
#include "windows/video/video-options.h"

namespace ift {

struct HostOptions {
  std::uint16_t port = 27184;
  std::uintptr_t window = 0;
  std::wstring title = L"In Falsus";
  std::wstring tracePath;
  std::wstring profilePath;
  std::wstring bindingsPath;
  bool syncBindings = true;
  bool usb = true;
  FieldConfig field;
  VideoOptions video;
  bool dryRun = false;
  bool list = false;
  bool videoDiagnostics = false;
  bool help = false;
  bool saveProfile = false;
  bool calibrate = false;
};

HostOptions parseOptions(int argc, wchar_t** argv);
void printHelp();

}
