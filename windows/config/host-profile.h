#pragma once

#include <string>
#include <string_view>

#include "windows/input/field-mapper.h"
#include "windows/video/video-options.h"

namespace ift {

struct HostProfile {
  FieldConfig field;
  VideoOptions video;
};

HostProfile parseProfile(std::string_view text);
std::string serializeProfile(const HostProfile& profile);

}
