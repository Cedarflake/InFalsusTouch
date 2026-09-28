#pragma once

#include <filesystem>

#include "windows/config/host-profile.h"

namespace ift {

std::filesystem::path defaultProfilePath();
HostProfile readProfileFile(const std::filesystem::path& path);
void writeProfileFile(const std::filesystem::path& path, const HostProfile& profile);

}
