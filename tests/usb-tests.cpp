#include "tests/test-support.h"
#include "windows/transport/usb-bridge.h"

#include <iostream>

int main() {
  try {
    const auto selected = ift::authorizedUsbDevices(
      "List of devices attached\nphoneA device model:munch\nphoneB unauthorized\n"
      "emulator-5554 device\n192.168.1.2:5555 device\nphoneC offline\n",
      {"PHONEA", "phoneB", "phoneC"});
    check(selected == std::vector<std::string>{"phoneA"}, "Only authorized physical USB phones are selected");
    check(ift::authorizedUsbDevices("fake\"serial device\n", {"fake\"serial"}).empty(), "Invalid serials must not become command arguments");
    check(ift::usbMapping("", 27184, 27184) == ift::UsbMapping::missing, "Lost forwarding must be recreated");
    check(ift::usbMapping("UsbFfs tcp:27184 tcp:27184\r\n", 27184, 27184) == ift::UsbMapping::ready, "Correct forwarding must be preserved");
    check(ift::usbMapping("UsbFfs tcp:27184 tcp:49152\n", 27184, 27184) == ift::UsbMapping::conflict, "A test or another Host mapping must not be overwritten");
    check(ift::usbMapping("UsbFfs tcp:27183 tcp:27183\n", 27184, 27184) == ift::UsbMapping::missing, "Video forwarding alone does not establish input forwarding");
    check(ift::usbMapping("UsbFfs tcp:27184 tcp:49152\n", 27184, 49152) == ift::UsbMapping::ready, "Custom Host ports must be supported");
    std::cout << "USB recovery tests passed\n";
    return 0;
  } catch (const std::exception& error) {
    std::cerr << error.what() << '\n';
    return 1;
  }
}
