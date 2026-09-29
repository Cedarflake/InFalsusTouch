#include "windows/transport/socket.h"
#include "windows/transport/video-server.h"

#include <chrono>
#include <deque>
#include <iostream>
#include <memory>

#include "protocol/cpp/video-packet.h"
#include "windows/capture/frame-converter.h"
#include "windows/encoder/hardware-encoder.h"
#include "windows/input/control-group.h"
#include "windows/video/frame-pacer.h"
#include "windows/video/frame-wait.h"

namespace ift {
namespace {

using Clock = std::chrono::steady_clock;
using namespace std::chrono_literals;
using Payload = std::shared_ptr<const std::vector<std::uint8_t>>;

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
  bool configured = false;
  std::uint32_t sequence = 0;
  Clock::time_point joined = Clock::now();
  std::deque<PendingVideo> pending;

  void queue(video::Header header, Payload payload) {
    if (pending.size() >= 3) throw std::runtime_error("Slow video viewer exceeded bounded queue");
    header.payloadSize = static_cast<std::uint32_t>(payload->size());
    pending.push_back({header, std::move(payload)});
  }

  void pump() {
    char unexpected = 0;
    const auto received = recv(socket.get(), &unexpected, 1, MSG_PEEK);
    if (received == 0) throw std::runtime_error("Video viewer closed");
    if (received > 0) throw std::runtime_error("Unexpected data on receive-only video socket");
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

struct Stream {
  Stream(HWND window, const GraphicsDevice& graphics, const VideoOptions& options)
    : encoder(graphics, options), converter(graphics, options), capture(window, graphics), pacing(options.fps) {}
  HardwareEncoder encoder;
  FrameConverter converter;
  WindowCapture capture;
  FramePacer pacing;
  Payload parameters;
  std::uint64_t lastEncoded = 0;
  std::uint64_t frames = 0;
  bool warmed = false;
  Clock::time_point started = Clock::now();
  Clock::time_point lastOutput = started;
  ~Stream() {
    const double seconds = std::chrono::duration<double>(Clock::now() - started).count();
    std::cout << "Video broadcast: " << frames << " encoded frames (" << frames / seconds << " fps), "
      << capture.received() << " WGC frames, " << capture.dropped() << " capture replacements" << std::endl;
  }
};

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
    << options.width << 'x' << options.height << '@' << options.fps << ", up to " << maxControllers << " viewers)" << std::endl;
  std::array<std::unique_ptr<Viewer>, maxControllers> viewers;
  std::unique_ptr<Stream> stream;
  FrameWait wake;
  while (!shutdown.requested()) {
    const SOCKET accepted = accept(listener.get(), nullptr, nullptr);
    if (accepted != INVALID_SOCKET) {
      std::size_t slot = 0;
      while (slot < viewers.size() && viewers[slot]) ++slot;
      if (slot == viewers.size()) closesocket(accepted);
      else viewers[slot] = std::make_unique<Viewer>(accepted);
    } else if (WSAGetLastError() != WSAEWOULDBLOCK) throw socketError("video accept");
    bool any = false;
    for (const auto& viewer : viewers) if (viewer) any = true;
    if (!any) {
      stream.reset();
      fd_set readable;
      FD_ZERO(&readable);
      FD_SET(listener.get(), &readable);
      timeval timeout{0, 20000};
      if (select(0, &readable, nullptr, nullptr, &timeout) == SOCKET_ERROR) throw socketError("video listener select");
      continue;
    }
    std::string failure;
    try {
      if (!stream) stream = std::make_unique<Stream>(window, graphics, options);
      for (auto& frame : stream->encoder.poll()) {
        if (frame.captureTimestamp <= stream->lastEncoded) throw std::runtime_error("Encoder reordered a frame");
        stream->lastEncoded = frame.captureTimestamp;
        const auto age = performanceNanoseconds() - frame.captureTimestamp;
        if ((stream->warmed || Clock::now() - stream->started > 2s) && age > 250'000'000) {
          throw std::runtime_error("Encoded frame exceeded 250 ms; restarting broadcast");
        }
        if (age < 100'000'000) stream->warmed = true;
        if (!stream->parameters) {
          auto parameters = stream->encoder.parameterSets();
          if (parameters.empty()) parameters = video::extractParameterSets(frame.bytes);
          if (!frame.keyFrame || !video::hasNal(parameters, 7) || !video::hasNal(parameters, 8)) {
            throw std::runtime_error("Encoder did not start with an IDR and SPS/PPS");
          }
          stream->parameters = std::make_shared<const std::vector<std::uint8_t>>(std::move(parameters));
        }
        const auto payload = std::make_shared<const std::vector<std::uint8_t>>(std::move(frame.bytes));
        for (auto& viewer : viewers) {
          if (!viewer) continue;
          try {
            video::Header header;
            header.width = options.width;
            header.height = options.height;
            header.fps = options.fps;
            header.bitrate = options.bitrate;
            if (!viewer->configured) {
              if (!frame.keyFrame) continue;
              header.type = video::Type::config;
              viewer->queue(header, stream->parameters);
              viewer->configured = true;
            }
            header.type = video::Type::frame;
            header.keyFrame = frame.keyFrame;
            header.sequence = ++viewer->sequence;
            header.captureTimestamp = frame.captureTimestamp;
            header.encodeTimestamp = frame.encodeTimestamp;
            header.presentationUs = frame.captureTimestamp / 1000;
            viewer->queue(header, payload);
          } catch (const std::exception& error) {
            viewer.reset();
            std::cerr << "Video viewer: " << error.what() << std::endl;
          }
        }
        ++stream->frames;
        stream->lastOutput = Clock::now();
      }
      const auto now = performanceNanoseconds();
      if (stream->encoder.canAccept() && stream->pacing.ready(now)) {
        auto frame = stream->capture.takeLatest();
        if (frame.owner) {
          const auto texture = stream->converter.convert(frame);
          stream->encoder.submit(texture.get(), frame.timestamp);
          stream->pacing.submitted(now);
          frame.owner.Close();
        }
      }
      if (stream->encoder.pending() && Clock::now() - stream->lastOutput > 2s) throw std::runtime_error("Hardware encoder stalled");
      if (!stream->parameters && Clock::now() - stream->started > 5s) throw std::runtime_error("No captured video within 5 seconds");
    } catch (const winrt::hresult_error& error) {
      failure = winrt::to_string(error.message());
    } catch (const std::exception& error) {
      failure = error.what();
    }
    if (!failure.empty()) {
      std::cerr << "Video broadcast: " << failure << std::endl;
      for (auto& viewer : viewers) viewer.reset();
      stream.reset();
      continue;
    }
    for (auto& viewer : viewers) {
      if (!viewer) continue;
      try { viewer->pump(); }
      catch (const std::exception& error) {
        viewer.reset();
        std::cerr << "Video viewer: " << error.what() << std::endl;
      }
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
