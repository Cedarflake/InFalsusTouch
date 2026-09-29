#pragma once

#include <atomic>
#include <stop_token>
#include <string>
#include <string_view>
#include <vector>

#include "windows/config/host-options.h"

namespace ift {

enum class UsbMapping { missing, ready, conflict };

std::vector<std::string> authorizedUsbDevices(std::string_view devices,
  const std::vector<std::string>& hardwareSerials);
UsbMapping usbMapping(std::string_view mappings, std::uint16_t devicePort, std::uint16_t hostPort);
void maintainUsb(const HostOptions& options, const std::atomic_bool& stopping, std::stop_token token);

}
