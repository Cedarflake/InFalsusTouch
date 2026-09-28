#include "windows/config/profile-file.h"

#include <Windows.h>
#include <ShlObj.h>

#include <array>
#include <fstream>
#include <stdexcept>
#include <system_error>

namespace ift {

std::filesystem::path defaultProfilePath() {
  PWSTR folder = nullptr;
  if (FAILED(SHGetKnownFolderPath(FOLDERID_LocalAppData, KF_FLAG_DEFAULT, nullptr, &folder))) {
    throw std::runtime_error("Cannot locate LocalAppData; use --profile PATH or --no-profile");
  }
  std::filesystem::path path;
  try { path = folder; }
  catch (...) { CoTaskMemFree(folder); throw; }
  CoTaskMemFree(folder);
  return path / L"InFalsusTouch" / L"host.ini";
}

HostProfile readProfileFile(const std::filesystem::path& path) {
  std::ifstream input(path, std::ios::binary);
  if (!input) throw std::runtime_error("Cannot open host profile; check --profile PATH or use --no-profile");
  std::array<char, 8193> buffer{};
  input.read(buffer.data(), static_cast<std::streamsize>(buffer.size()));
  if (input.bad()) throw std::runtime_error("Cannot read host profile");
  return parseProfile({buffer.data(), static_cast<std::size_t>(input.gcount())});
}

void writeProfileFile(const std::filesystem::path& path, const HostProfile& profile) {
  const auto text = serializeProfile(profile);
  if (path.empty()) throw std::invalid_argument("A profile path is required");
  if (path.has_parent_path()) std::filesystem::create_directories(path.parent_path());
  auto temporary = path;
  temporary += L"." + std::to_wstring(GetCurrentProcessId()) + L".tmp";
  // CREATE_NEW avoids replacing a stale file or following a pre-existing temporary file.
  const auto file = CreateFileW(temporary.c_str(), GENERIC_WRITE, 0, nullptr, CREATE_NEW, FILE_ATTRIBUTE_NORMAL, nullptr);
  if (file == INVALID_HANDLE_VALUE) throw std::system_error(GetLastError(), std::system_category(), "Create profile temporary file");
  DWORD written = 0;
  const bool wrote = WriteFile(file, text.data(), static_cast<DWORD>(text.size()), &written, nullptr) && written == text.size();
  const bool flushed = wrote && FlushFileBuffers(file);
  const auto error = GetLastError();
  CloseHandle(file);
  if (!flushed) {
    DeleteFileW(temporary.c_str());
    throw std::system_error(error, std::system_category(), "Write host profile");
  }
  if (!MoveFileExW(temporary.c_str(), path.c_str(), MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH)) {
    const auto moveError = GetLastError();
    DeleteFileW(temporary.c_str());
    throw std::system_error(moveError, std::system_category(), "Replace host profile");
  }
}

}
