#include "windows/transport/usb-bridge.h"

#include <Windows.h>
#include <SetupAPI.h>

#include <algorithm>
#include <array>
#include <chrono>
#include <filesystem>
#include <iostream>
#include <map>
#include <memory>
#include <sstream>
#include <stdexcept>
#include <thread>

namespace ift {
namespace {

using namespace std::chrono_literals;
using Handle = std::unique_ptr<void, decltype(&CloseHandle)>;

bool stopped(const std::atomic_bool& stopping, std::stop_token token) {
  return stopping.load() || token.stop_requested();
}

std::filesystem::path findAdb() {
  std::array<wchar_t, 32768> path{};
  const auto length = GetModuleFileNameW(nullptr, path.data(), static_cast<DWORD>(path.size()));
  auto directory = std::filesystem::path(std::wstring(path.data(), length)).parent_path();
  for (int depth = 0; depth < 6 && !directory.empty(); ++depth) {
    for (const auto& relative : {L"platform-tools/adb.exe", L"adb.exe", L".tools/android-sdk/platform-tools/adb.exe"}) {
      const auto candidate = directory / relative;
      if (std::filesystem::is_regular_file(candidate)) return candidate;
    }
    const auto parent = directory.parent_path();
    if (parent == directory) break;
    directory = parent;
  }
  const auto found = SearchPathW(nullptr, L"adb.exe", nullptr, static_cast<DWORD>(path.size()), path.data(), nullptr);
  if (found && found < path.size()) return std::filesystem::path(path.data());
  throw std::runtime_error("ADB not found; keep the platform-tools folder beside InFalsusTouchHost.exe");
}

std::string runAdb(const std::filesystem::path& executable, const std::vector<std::wstring>& arguments,
  const std::atomic_bool& stopping, std::stop_token token) {
  SECURITY_ATTRIBUTES security{sizeof(SECURITY_ATTRIBUTES), nullptr, TRUE};
  HANDLE reader = nullptr, writer = nullptr;
  if (!CreatePipe(&reader, &writer, &security, 0)) throw std::runtime_error("Cannot create ADB output pipe");
  Handle input(reader, CloseHandle), output(writer, CloseHandle);
  if (!SetHandleInformation(input.get(), HANDLE_FLAG_INHERIT, 0)) throw std::runtime_error("Cannot protect ADB output pipe");
  Handle nullInput(CreateFileW(L"NUL", GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE,
    &security, OPEN_EXISTING, 0, nullptr), CloseHandle);
  if (nullInput.get() == INVALID_HANDLE_VALUE) throw std::runtime_error("Cannot create ADB input handle");
  SIZE_T attributeBytes = 0;
  InitializeProcThreadAttributeList(nullptr, 1, 0, &attributeBytes);
  std::vector<std::byte> attributeStorage(attributeBytes);
  auto* attributes = reinterpret_cast<LPPROC_THREAD_ATTRIBUTE_LIST>(attributeStorage.data());
  if (!InitializeProcThreadAttributeList(attributes, 1, 0, &attributeBytes)) {
    throw std::runtime_error("Cannot initialize ADB process attributes");
  }
  const auto deleteAttributes = [](LPPROC_THREAD_ATTRIBUTE_LIST value) { DeleteProcThreadAttributeList(value); };
  std::unique_ptr<_PROC_THREAD_ATTRIBUTE_LIST, decltype(deleteAttributes)> attributeOwner(attributes, deleteAttributes);
  HANDLE inherited[]{output.get(), nullInput.get()};
  if (!UpdateProcThreadAttribute(attributes, 0, PROC_THREAD_ATTRIBUTE_HANDLE_LIST, inherited,
      sizeof(inherited), nullptr, nullptr)) throw std::runtime_error("Cannot restrict ADB handle inheritance");
  STARTUPINFOEXW startup{};
  startup.StartupInfo.cb = sizeof(startup);
  startup.StartupInfo.dwFlags = STARTF_USESTDHANDLES;
  startup.StartupInfo.hStdInput = nullInput.get();
  startup.StartupInfo.hStdOutput = output.get();
  startup.StartupInfo.hStdError = output.get();
  startup.lpAttributeList = attributes;
  std::wstring command = L"\"" + executable.wstring() + L"\" -L tcp:localhost:5037";
  for (const auto& argument : arguments) command += L" \"" + argument + L"\"";
  PROCESS_INFORMATION process{};
  if (!CreateProcessW(executable.c_str(), command.data(), nullptr, nullptr, TRUE,
      CREATE_NO_WINDOW | EXTENDED_STARTUPINFO_PRESENT, nullptr, executable.parent_path().c_str(),
      &startup.StartupInfo, &process)) throw std::runtime_error("Cannot start ADB");
  Handle child(process.hProcess, CloseHandle), thread(process.hThread, CloseHandle);
  output.reset();
  const auto deadline = std::chrono::steady_clock::now() + 8s;
  std::string result;
  try {
    while (true) {
      DWORD available = 0;
      if (PeekNamedPipe(input.get(), nullptr, 0, nullptr, &available, nullptr) && available) {
        std::array<char, 4096> bytes{};
        DWORD count = 0;
        if (!ReadFile(input.get(), bytes.data(), std::min<DWORD>(available, static_cast<DWORD>(bytes.size())), &count, nullptr)) {
          throw std::runtime_error("Cannot read ADB output");
        }
        result.append(bytes.data(), count);
        if (result.size() > 65536) throw std::runtime_error("ADB output exceeded 64 KiB");
        continue;
      }
      if (WaitForSingleObject(child.get(), 20) == WAIT_OBJECT_0) {
        if (PeekNamedPipe(input.get(), nullptr, 0, nullptr, &available, nullptr) && available) continue;
        DWORD code = 1;
        GetExitCodeProcess(child.get(), &code);
        if (code != 0) throw std::runtime_error(result.empty() ? "ADB command failed" : result);
        return result;
      }
      if (stopped(stopping, token) || std::chrono::steady_clock::now() >= deadline) {
        throw std::runtime_error("ADB command interrupted or timed out");
      }
    }
  } catch (...) {
    // Only the short-lived client belongs to this operation; never kill the shared ADB server.
    TerminateProcess(child.get(), 1);
    WaitForSingleObject(child.get(), 1000);
    throw;
  }
}

std::vector<std::string> usbSerials() {
  const auto devices = SetupDiGetClassDevsW(nullptr, L"USB", nullptr, DIGCF_ALLCLASSES | DIGCF_PRESENT);
  if (devices == INVALID_HANDLE_VALUE) throw std::runtime_error("Cannot enumerate USB devices");
  const auto destroy = [](void* value) { SetupDiDestroyDeviceInfoList(value); };
  std::unique_ptr<void, decltype(destroy)> owner(devices, destroy);
  std::vector<std::string> serials;
  SP_DEVINFO_DATA info{};
  info.cbSize = sizeof(info);
  for (DWORD index = 0; SetupDiEnumDeviceInfo(devices, index, &info); ++index) {
    std::array<wchar_t, 4096> name{};
    if (!SetupDiGetDeviceInstanceIdW(devices, &info, name.data(), static_cast<DWORD>(name.size()), nullptr)) continue;
    const std::wstring instance(name.data());
    const auto serial = instance.substr(instance.find_last_of(L'\\') + 1);
    if (std::all_of(serial.begin(), serial.end(), [](wchar_t value) { return value > 0 && value < 128; })) {
      std::string ascii;
      for (const auto value : serial) ascii.push_back(static_cast<char>(value));
      serials.push_back(std::move(ascii));
    }
  }
  return serials;
}

}

std::vector<std::string> authorizedUsbDevices(std::string_view devices,
  const std::vector<std::string>& hardwareSerials) {
  std::vector<std::string> result;
  std::istringstream lines{std::string(devices)};
  for (std::string line; std::getline(lines, line);) {
    std::istringstream fields(line);
    std::string serial, state;
    fields >> serial >> state;
    if (state != "device" || serial.find_first_not_of("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-") != std::string::npos) continue;
    const bool usb = std::any_of(hardwareSerials.begin(), hardwareSerials.end(), [&](const auto& hardware) {
      return _stricmp(serial.c_str(), hardware.c_str()) == 0;
    });
    if (usb && std::find(result.begin(), result.end(), serial) == result.end()) result.push_back(serial);
    if (result.size() == 7) break;
  }
  return result;
}

UsbMapping usbMapping(std::string_view mappings, std::uint16_t devicePort, std::uint16_t hostPort) {
  std::istringstream lines{std::string(mappings)};
  const auto local = "tcp:" + std::to_string(devicePort);
  const auto remote = "tcp:" + std::to_string(hostPort);
  for (std::string line; std::getline(lines, line);) {
    std::istringstream fields(line);
    std::string serial, from, to;
    fields >> serial >> from >> to;
    if (from == local) return to == remote ? UsbMapping::ready : UsbMapping::conflict;
  }
  return UsbMapping::missing;
}

void maintainUsb(const HostOptions& options, const std::atomic_bool& stopping, std::stop_token token) {
  SetThreadPriority(GetCurrentThread(), THREAD_PRIORITY_BELOW_NORMAL);
  // ADB 37 changed the Windows USB backend. Prefer its native compatibility backend
  // for servers we start; existing servers and explicit user overrides are preserved.
  if (!GetEnvironmentVariableW(L"ADB_USB_LEGACY", nullptr, 0) && GetLastError() == ERROR_ENVVAR_NOT_FOUND) {
    SetEnvironmentVariableW(L"ADB_USB_LEGACY", L"1");
  }
  std::map<std::string, std::string> states;
  const auto report = [&](const std::string& key, const std::string& state) {
    if (states[key] == state) return;
    states[key] = state;
    std::cout << "USB: " << state << std::endl;
  };
  while (!stopped(stopping, token)) {
    try {
      const auto adb = findAdb();
      const auto devices = authorizedUsbDevices(runAdb(adb, {L"devices", L"-l"}, stopping, token), usbSerials());
      report("server", devices.empty() ? "waiting for an authorized USB phone; confirm USB debugging on the phone" : "automatic connection recovery active");
      for (auto item = states.begin(); item != states.end();) {
        if (item->first != "server" && std::find(devices.begin(), devices.end(), item->first) == devices.end()) item = states.erase(item);
        else ++item;
      }
      for (const auto& serial : devices) {
        if (stopped(stopping, token)) break;
        try {
          const std::wstring device(serial.begin(), serial.end());
          auto mappings = runAdb(adb, {L"-s", device, L"reverse", L"--list"}, stopping, token);
          const std::array<std::pair<std::uint16_t, std::uint16_t>, 2> ports{{
            {std::uint16_t{27184}, options.port}, {std::uint16_t{27183}, options.video.port},
          }};
          bool changed = false;
          for (const auto& [phonePort, hostPort] : ports) {
            if (phonePort == 27183 && !options.video.enabled) continue;
            const auto status = usbMapping(mappings, phonePort, hostPort);
            if (status == UsbMapping::conflict) throw std::runtime_error("port " + std::to_string(phonePort) + " is mapped to another Host; use --no-usb for manual mappings");
            if (status == UsbMapping::ready) continue;
            runAdb(adb, {L"-s", device, L"reverse", L"--no-rebind", L"tcp:" + std::to_wstring(phonePort), L"tcp:" + std::to_wstring(hostPort)}, stopping, token);
            changed = true;
          }
          if (changed) {
            mappings = runAdb(adb, {L"-s", device, L"reverse", L"--list"}, stopping, token);
            for (const auto& [phonePort, hostPort] : ports) {
              if (phonePort == 27183 && !options.video.enabled) continue;
              if (usbMapping(mappings, phonePort, hostPort) != UsbMapping::ready) throw std::runtime_error("USB forwarding verification failed");
            }
            states.erase(serial);
          }
          report(serial, serial + " ready");
        } catch (const std::exception& error) {
          if (!stopped(stopping, token)) report(serial, serial + ": " + error.what());
        }
      }
    } catch (const std::exception& error) {
      if (!stopped(stopping, token)) report("server", error.what());
    }
    for (int tick = 0; tick < 20 && !stopped(stopping, token); ++tick) std::this_thread::sleep_for(100ms);
  }
}

}
