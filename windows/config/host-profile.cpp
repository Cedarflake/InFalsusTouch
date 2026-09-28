#include "windows/config/host-profile.h"

#include <charconv>
#include <iomanip>
#include <locale>
#include <set>
#include <sstream>
#include <stdexcept>

namespace ift {
namespace {

std::string_view trim(std::string_view value) {
  const auto start = value.find_first_not_of(" \t\r");
  if (start == std::string_view::npos) return {};
  const auto end = value.find_last_not_of(" \t\r");
  return value.substr(start, end - start + 1);
}

template<class Number>
Number number(std::string_view text) {
  Number result{};
  const auto parsed = std::from_chars(text.data(), text.data() + text.size(), result);
  if (parsed.ec != std::errc{} || parsed.ptr != text.data() + text.size()) {
    throw std::invalid_argument("Invalid profile number");
  }
  return result;
}

void validate(const HostProfile& profile) {
  profile.field.validate();
  const auto& video = profile.video;
  if (!((video.width == 1280 && video.height == 720) || (video.width == 1920 && video.height == 1080)) ||
      video.fps < 24 || video.fps > 60 || video.bitrate < 500'000 || video.bitrate > 40'000'000) {
    throw std::invalid_argument("Invalid video profile; expected 720p/1080p, 24-60 FPS, 0.5-40 Mbps");
  }
}

}

HostProfile parseProfile(std::string_view text) {
  if (text.size() > 8192) throw std::invalid_argument("Host profile is too large");
  HostProfile profile;
  std::set<std::string> keys;
  while (!text.empty()) {
    const auto newline = text.find('\n');
    const auto line = trim(text.substr(0, newline));
    text = newline == std::string_view::npos ? std::string_view{} : text.substr(newline + 1);
    if (line.empty() || line.front() == '#') continue;
    const auto separator = line.find('=');
    if (separator == std::string_view::npos) throw std::invalid_argument("Expected key=value in host profile");
    const auto key = trim(line.substr(0, separator));
    const auto value = trim(line.substr(separator + 1));
    if (!keys.emplace(key).second) throw std::invalid_argument("Duplicate host profile key");
    if (key == "version") {
      if (value != "1") throw std::invalid_argument("Unsupported host profile version");
    } else if (key == "field-left") profile.field.left = number<double>(value);
    else if (key == "field-right") profile.field.right = number<double>(value);
    else if (key == "field-y") profile.field.y = number<double>(value);
    else if (key == "sensitivity") profile.field.sensitivity = number<double>(value);
    else if (key == "acceleration") profile.field.acceleration = number<double>(value);
    else if (key == "smoothing") profile.field.smoothing = number<double>(value);
    else if (key == "max-speed") profile.field.maxSpeed = number<double>(value);
    else if (key == "resolution") {
      if (value == "720p") { profile.video.width = 1280; profile.video.height = 720; }
      else if (value == "1080p") { profile.video.width = 1920; profile.video.height = 1080; }
      else throw std::invalid_argument("Profile resolution must be 720p or 1080p");
    } else if (key == "fps") profile.video.fps = number<std::uint16_t>(value);
    else if (key == "bitrate") profile.video.bitrate = number<std::uint32_t>(value);
    else throw std::invalid_argument("Unknown host profile key");
  }
  if (!keys.contains("version")) throw std::invalid_argument("Host profile is missing version=1");
  validate(profile);
  return profile;
}

std::string serializeProfile(const HostProfile& profile) {
  validate(profile);
  std::ostringstream output;
  output.imbue(std::locale::classic());
  output << std::setprecision(17) << "version=1\n"
    << "field-left=" << profile.field.left << '\n'
    << "field-right=" << profile.field.right << '\n'
    << "field-y=" << profile.field.y << '\n'
    << "sensitivity=" << profile.field.sensitivity << '\n'
    << "acceleration=" << profile.field.acceleration << '\n'
    << "smoothing=" << profile.field.smoothing << '\n'
    << "max-speed=" << profile.field.maxSpeed << '\n'
    << "resolution=" << (profile.video.height == 720 ? "720p" : "1080p") << '\n'
    << "fps=" << profile.video.fps << '\n'
    << "bitrate=" << profile.video.bitrate << '\n';
  return output.str();
}

}
