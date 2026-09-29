#include "windows/transport/socket.h"
#include "windows/transport/video-server.h"

#include <chrono>
#include <algorithm>
#include <deque>
#include <iostream>
#include <memory>
#include <map>
#include <optional>
#include <syncstream>

#include "protocol/cpp/video-packet.h"
#include "windows/capture/frame-converter.h"
#include "windows/video/encoding-group.h"
#include "windows/input/control-group.h"
#include "windows/video/frame-pacer.h"
#include "windows/video/frame-wait.h"

namespace ift {
namespace {

using Clock = std::chrono::steady_clock;
using namespace std::chrono_literals;
using Payload = VideoPayload;

struct Shutdown {
  const std::atomic_bool& stopping;
  std::stop_token token;
  bool requested() const { return stopping.load() || token.stop_requested(); }
};

struct PendingVideo {
  video::Header header;
  Payload payload;
  video::HeaderBytes bytes{};
  std::size_t offset = 0;
  Clock::time_point deadline = Clock::now() + 100ms;
};

struct Viewer {
  explicit Viewer(SOCKET accepted) : socket(accepted) {
    socket.nonblocking();
    const BOOL noDelay = TRUE;
    const int sendBuffer = 32768;
    if (setsockopt(socket.get(), IPPROTO_TCP, TCP_NODELAY, reinterpret_cast<const char*>(&noDelay), sizeof(noDelay)) ||
        setsockopt(socket.get(), SOL_SOCKET, SO_SNDBUF, reinterpret_cast<const char*>(&sendBuffer), sizeof(sendBuffer))) {
      throw socketError("video socket options");
    }
  }
  Socket socket;
  video::RequestBytes request{};
  std::size_t received = 0;
  std::optional<std::uint16_t> fps;
  bool configured = false;
  std::uint32_t sequence = 0;
  Clock::time_point joined = Clock::now();
  std::deque<PendingVideo> pending;

  void queue(video::Header header, Payload payload) {
    if (pending.size() >= 3) throw std::runtime_error("Slow video viewer exceeded bounded queue");
    header.payloadSize = static_cast<std::uint32_t>(payload->size());
    pending.push_back({header, std::move(payload)});
  }

  void pump(std::uint16_t fallback) {
    if (!fps) {
      if (Clock::now() - joined > 2s) throw std::runtime_error("Video subscription timeout");
      const auto count = recv(socket.get(), reinterpret_cast<char*>(request.data() + received),
        static_cast<int>(request.size() - received), 0);
      if (count == SOCKET_ERROR && WSAGetLastError() == WSAEWOULDBLOCK) return;
      if (count <= 0) throw std::runtime_error("Video subscription closed");
      received += static_cast<std::size_t>(count);
      if (received != request.size()) return;
      const auto requested = video::decodeRequest(request);
      fps = requested == 0 ? fallback : requested;
      joined = Clock::now();
    }
    char unexpected = 0;
    const auto peeked = recv(socket.get(), &unexpected, 1, MSG_PEEK);
    if (peeked == 0) throw std::runtime_error("Video viewer closed");
    if (peeked > 0) throw std::runtime_error("Unexpected data after video subscription");
    if (WSAGetLastError() != WSAEWOULDBLOCK) throw socketError("video peer");
    if (!configured && Clock::now() - joined > 5s) throw std::runtime_error("No IDR for new video viewer within 5 seconds");
    std::size_t budget = 256 * 1024;
    while (!pending.empty() && budget > 0) {
      auto& packet = pending.front();
      if (Clock::now() > packet.deadline) throw std::runtime_error("Video send exceeded 100 ms; reconnect for a fresh IDR");
      if (packet.offset == 0) {
        packet.header.sendTimestamp = performanceNanoseconds();
        packet.bytes = video::encodeHeader(packet.header);
      }
      const bool isHeader = packet.offset < video::headerSize;
      const auto data = isHeader ? std::span<const std::uint8_t>(packet.bytes).subspan(packet.offset)
        : std::span<const std::uint8_t>(*packet.payload).subspan(packet.offset - video::headerSize);
      const auto count = std::min(data.size(), budget);
      const int sent = send(socket.get(), reinterpret_cast<const char*>(data.data()), static_cast<int>(count), 0);
      if (sent == SOCKET_ERROR && WSAGetLastError() == WSAEWOULDBLOCK) return;
      if (sent <= 0) throw socketError("video send");
      packet.offset += static_cast<std::size_t>(sent);
      budget -= static_cast<std::size_t>(sent);
      if (packet.offset == video::headerSize + packet.payload->size()) pending.pop_front();
    }
  }
};

struct Group {
  Group(const GraphicsDevice& graphics, VideoOptions options)
    : fps(options.fps), encoder(graphics, options), pacing(options.fps) {}
  std::uint16_t fps;
  EncodingGroup encoder;
  FramePacer pacing;
  std::uint64_t lastCapture = 0;
  std::optional<Clock::time_point> idleSince;
};

struct Capture {
  Capture(HWND window, const GraphicsDevice& graphics, VideoOptions options)
    : converter(graphics, options), source(window, graphics) {
    std::osyncstream(std::cout) << "Video capture started" << std::endl;
  }
  FrameConverter converter;
  WindowCapture source;
  winrt::com_ptr<ID3D11Texture2D> texture;
  std::uint64_t timestamp = 0;
  ~Capture() {
    std::osyncstream(std::cout) << "Video capture stopped: " << source.received() << " WGC frames, "
      << source.dropped() << " capture replacements" << std::endl;
  }
  void refresh() {
    auto frame = source.takeLatest();
    if (!frame.owner) return;
    try {
      texture = converter.convert(frame);
      timestamp = frame.timestamp;
    } catch (...) {
      frame.owner.Close();
      throw;
    }
    frame.owner.Close();
  }
};

void distribute(const GroupFrame& frame, std::uint16_t fps, const VideoOptions& options,
                std::array<std::unique_ptr<Viewer>, maxControllers>& viewers) {
  for (auto& viewer : viewers) {
    if (!viewer || viewer->fps != fps) continue;
    try {
      video::Header header;
      header.width = options.width;
      header.height = options.height;
      header.fps = fps;
      header.bitrate = options.bitrate;
      if (!viewer->configured) {
        if (!frame.keyFrame) continue;
        header.type = video::Type::config;
        viewer->queue(header, frame.parameters);
        viewer->configured = true;
      }
      header.type = video::Type::frame;
      header.keyFrame = frame.keyFrame;
      header.sequence = ++viewer->sequence;
      header.captureTimestamp = frame.captureTimestamp;
      header.encodeTimestamp = frame.encodeTimestamp;
      header.presentationUs = frame.captureTimestamp / 1000;
      viewer->queue(header, frame.payload);
    } catch (const std::exception& error) {
      viewer.reset();
      std::cerr << "Video viewer: " << error.what() << std::endl;
    }
  }
}

void serve(VideoOptions options, HWND window, const Shutdown& shutdown) {
  MediaRuntime runtime;
  const auto graphics = createGraphicsDevice();
  Winsock winsock;
  Socket listener(socket(AF_INET, SOCK_STREAM, IPPROTO_TCP));
  const BOOL exclusive = TRUE;
  if (setsockopt(listener.get(), SOL_SOCKET, SO_EXCLUSIVEADDRUSE,
      reinterpret_cast<const char*>(&exclusive), sizeof(exclusive)) != 0) throw socketError("video exclusive bind");
  sockaddr_in address{};
  address.sin_family = AF_INET;
  address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
  address.sin_port = htons(options.port);
  if (bind(listener.get(), reinterpret_cast<sockaddr*>(&address), sizeof(address)) != 0 ||
      listen(listener.get(), static_cast<int>(maxControllers)) != 0) throw socketError("video bind/listen");
  listener.nonblocking();
  std::cout << "Video listening on 127.0.0.1:" << options.port << " ("
    << options.width << 'x' << options.height << ", on-demand FPS groups, up to " << maxControllers << " viewers)" << std::endl;
  std::array<std::unique_ptr<Viewer>, maxControllers> viewers;
  std::unique_ptr<Capture> capture;
  std::map<std::uint16_t, std::unique_ptr<Group>> groups;
  std::vector<std::unique_ptr<Group>> retiring;
  const auto retire = [&](auto it) {
    it->second->encoder.stop();
    retiring.push_back(std::move(it->second));
    return groups.erase(it);
  };
  const auto pump = [&] {
    for (auto& viewer : viewers) {
      if (!viewer) continue;
      try { viewer->pump(options.fps); }
      catch (const std::exception& error) {
        viewer.reset();
        std::cerr << "Video viewer: " << error.what() << std::endl;
      }
    }
  };
  FrameWait wake;
  while (!shutdown.requested()) {
    pump();
    const bool watching = std::any_of(viewers.begin(), viewers.end(), [](const auto& viewer) { return viewer && viewer->fps; });
    for (auto it = groups.begin(); it != groups.end();) {
      const bool subscribed = std::any_of(viewers.begin(), viewers.end(), [&](const auto& viewer) {
        return viewer && viewer->fps == it->first;
      });
      if (subscribed) it->second->idleSince.reset();
      else if (!it->second->idleSince) it->second->idleSince = Clock::now();
      // Driver teardown can disturb other streams. Retain idle groups without encoding until nobody is watching.
      if (!watching && it->second->idleSince && Clock::now() - *it->second->idleSince >= 2s) it = retire(it);
      else ++it;
    }
    std::erase_if(retiring, [](const auto& group) { return group->encoder.stopped(); });
    const SOCKET accepted = accept(listener.get(), nullptr, nullptr);
    if (accepted != INVALID_SOCKET) {
      std::size_t slot = 0;
      while (slot < viewers.size() && viewers[slot]) ++slot;
      if (slot == viewers.size()) closesocket(accepted);
      else viewers[slot] = std::make_unique<Viewer>(accepted);
    } else if (WSAGetLastError() != WSAEWOULDBLOCK) throw socketError("video accept");
    for (const auto& viewer : viewers) {
      if (!viewer || !viewer->fps || groups.contains(*viewer->fps)) continue;
      const auto fps = *viewer->fps;
      // Shutdown may involve the driver; retire off-thread before reusing its encoder slot.
      if (groups.size() + retiring.size() >= maxControllers) {
        const auto idle = std::find_if(groups.begin(), groups.end(), [](const auto& entry) { return entry.second->idleSince.has_value(); });
        if (idle != groups.end()) retire(idle);
        continue;
      }
      if (std::any_of(retiring.begin(), retiring.end(), [fps](const auto& group) { return group->fps == fps; })) continue;
      auto groupOptions = options;
      groupOptions.fps = fps;
      groups.emplace(fps, std::make_unique<Group>(graphics, groupOptions));
      std::osyncstream(std::cout) << "Video group " << fps << " FPS created" << std::endl;
    }
    for (auto it = groups.begin(); it != groups.end();) {
      auto output = it->second->encoder.poll();
      if (!output.failure.empty()) {
        const auto fps = it->first;
        std::cerr << "Video group " << fps << " FPS failed: " << output.failure << std::endl;
        for (auto& viewer : viewers) if (viewer && viewer->fps == fps) viewer.reset();
        it = retire(it);
        continue;
      }
      for (const auto& frame : output.frames) distribute(frame, it->first, options, viewers);
      ++it;
    }
    pump();
    if (std::none_of(groups.begin(), groups.end(), [](const auto& entry) { return !entry.second->idleSince; })) {
      capture.reset();
      const bool any = std::any_of(viewers.begin(), viewers.end(), [](const auto& viewer) { return !!viewer; });
      if (any) wake.wait();
      else {
        fd_set readable;
        FD_ZERO(&readable);
        FD_SET(listener.get(), &readable);
        timeval timeout{0, 20000};
        if (select(0, &readable, nullptr, nullptr, &timeout) == SOCKET_ERROR) throw socketError("video listener select");
      }
      continue;
    }
    std::string failure;
    try {
      if (!capture) {
        capture = std::make_unique<Capture>(window, graphics, options);
      }
      const auto now = performanceNanoseconds();
      if (std::any_of(groups.begin(), groups.end(), [now](const auto& entry) {
          return !entry.second->idleSince && entry.second->pacing.ready(now);
        })) {
        capture->refresh();
        // Each group keeps its own deadlines across other viewers joining and leaving.
        // Retain the converted frame so a faster group cannot consume a slower group's input.
        for (auto& [fps, group] : groups) {
          if (group->idleSince || !group->pacing.ready(now) || capture->timestamp <= group->lastCapture) continue;
          group->encoder.submit(capture->texture, capture->timestamp);
          group->lastCapture = capture->timestamp;
          group->pacing.submitted(now);
        }
      }
    } catch (const winrt::hresult_error& error) {
      failure = winrt::to_string(error.message());
    } catch (const std::exception& error) {
      failure = error.what();
    }
    if (!failure.empty()) {
      std::cerr << "Video capture: " << failure << std::endl;
      for (auto& viewer : viewers) viewer.reset();
      for (auto it = groups.begin(); it != groups.end();) it = retire(it);
      capture.reset();
    }
    wake.wait();
  }
}

}

void runVideoServer(VideoOptions options, HWND window, const std::atomic_bool& stopping,
                    std::stop_token token) noexcept {
  try { serve(options, window, {stopping, token}); }
  catch (const winrt::hresult_error& error) { std::cerr << "Video unavailable: " << winrt::to_string(error.message()) << std::endl; }
  catch (const std::exception& error) { std::cerr << "Video unavailable: " << error.what() << std::endl; }
}

}
