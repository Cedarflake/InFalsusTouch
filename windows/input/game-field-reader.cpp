#include "windows/input/game-field-reader.h"

#include <TlHelp32.h>
#include <bcrypt.h>

#include <algorithm>
#include <array>
#include <cmath>
#include <cstring>
#include <filesystem>
#include <fstream>

namespace ift {
namespace {

bool supportedBinary(const std::filesystem::path& path) {
  // Offsets are valid only for the inspected In Falsus 1.0.4b Windows binary.
  constexpr std::array<unsigned char, 32> expected{
    0xab, 0x1d, 0x8f, 0xa7, 0x73, 0x90, 0x78, 0x51, 0x0f, 0xab, 0x5f, 0x85, 0x80, 0x09, 0x5c, 0x90,
    0xea, 0x0f, 0x5e, 0x92, 0x36, 0xac, 0x9b, 0x91, 0x8e, 0x93, 0x4c, 0xb9, 0xae, 0x1b, 0x7d, 0x9c,
  };
  struct Hash {
    BCRYPT_ALG_HANDLE algorithm = nullptr;
    BCRYPT_HASH_HANDLE value = nullptr;
    ~Hash() {
      if (value) BCryptDestroyHash(value);
      if (algorithm) BCryptCloseAlgorithmProvider(algorithm, 0);
    }
  } hash;
  if (BCryptOpenAlgorithmProvider(&hash.algorithm, BCRYPT_SHA256_ALGORITHM, nullptr, 0) < 0 ||
      BCryptCreateHash(hash.algorithm, &hash.value, nullptr, 0, nullptr, 0, 0) < 0) return false;
  std::ifstream stream(path, std::ios::binary);
  if (!stream) return false;
  std::array<unsigned char, 65536> buffer{};
  while (stream) {
    stream.read(reinterpret_cast<char*>(buffer.data()), buffer.size());
    const auto count = static_cast<ULONG>(stream.gcount());
    if (count && BCryptHashData(hash.value, buffer.data(), count, 0) < 0) return false;
  }
  std::array<unsigned char, 32> digest{};
  return !stream.bad() && BCryptFinishHash(hash.value, digest.data(), static_cast<ULONG>(digest.size()), 0) >= 0 &&
    digest == expected;
}

}

GameFieldReader::GameFieldReader(HWND window) {
  try { initialize(window); }
  catch (const std::exception& error) { module_ = 0; problem_ = error.what(); }
}

GameFieldReader::~GameFieldReader() {
  if (process_) CloseHandle(process_);
}

void GameFieldReader::initialize(HWND window) {
  DWORD pid = 0;
  GetWindowThreadProcessId(window, &pid);
  process_ = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION | PROCESS_VM_READ, FALSE, pid);
  if (!process_) { problem_ = "Cannot read the selected game's input state"; return; }
  std::array<wchar_t, 32768> path{};
  DWORD pathLength = static_cast<DWORD>(path.size());
  if (!QueryFullProcessImageNameW(process_, 0, path.data(), &pathLength)) {
    problem_ = "Cannot identify the selected game process";
    return;
  }
  const std::filesystem::path executable(std::wstring(path.data(), pathLength));
  isGame_ = _wcsicmp(executable.filename().c_str(), L"infalsus.exe") == 0;
  if (!isGame_) return;
  problem_ = "Absolute Field needs a supported In Falsus build; use Relative for this version";
  const HANDLE modules = CreateToolhelp32Snapshot(TH32CS_SNAPMODULE, pid);
  if (modules == INVALID_HANDLE_VALUE) return;
  MODULEENTRY32W entry{};
  entry.dwSize = sizeof(entry);
  std::uintptr_t candidate = 0;
  std::filesystem::path binary;
  if (Module32FirstW(modules, &entry)) {
    do {
      if (_wcsicmp(entry.szModule, L"GameAssembly.dll") == 0) {
        candidate = reinterpret_cast<std::uintptr_t>(entry.modBaseAddr);
        binary = entry.szExePath;
        break;
      }
    } while (Module32NextW(modules, &entry));
  }
  CloseHandle(modules);
  if (!candidate || binary.parent_path() != executable.parent_path() || !supportedBinary(binary)) return;
  module_ = candidate;
  problem_.clear();
}

std::optional<FieldFeedback> GameFieldReader::sample() const noexcept {
  if (!module_) return {};
  std::uintptr_t sceneClass = 0, name = 0, statics = 0, instance = 0, field = 0;
  std::array<char, 10> sceneName{};
  if (!read(module_ + 0x319e300, sceneClass) || !read(sceneClass + 0x10, name) ||
      !read(name, sceneName) || std::memcmp(sceneName.data(), "GameScene", 10) != 0 ||
      !read(sceneClass + 0xb8, statics) || !read(statics, instance) || !instance ||
      !read(statics + 0x1b8, field)) return {};
  for (int attempt = 0; attempt < 3; ++attempt) {
    float position = 0, visible = 0, checkPosition = 0;
    double sensitivity = 0;
    std::uintptr_t checkField = 0;
    if (!read(field + 0x10, position) || !read(field + 0x98, visible) ||
        !read(field + 0xb8, sensitivity) || !read(field + 0x10, checkPosition) ||
        !read(statics + 0x1b8, checkField)) return {};
    if (field == checkField && position == checkPosition && std::isfinite(position) &&
        std::isfinite(visible) && std::isfinite(sensitivity) && sensitivity >= 0.01 && sensitivity <= 100 &&
        std::abs(std::clamp(position, 0.0f, 1.0f) - visible) < 0.00001f) {
      return FieldFeedback{field, position, sensitivity};
    }
  }
  return {};
}

}
