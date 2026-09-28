#include "windows/transport/socket.h"
#include "windows/transport/video-server.h"

#include <chrono>
#include <iostream>
#include <thread>

#include "protocol/cpp/video-packet.h"
#include "windows/capture/frame-converter.h"
#include "windows/encoder/hardware-encoder.h"
#include "windows/video/frame-wait.h"

namespace ift {
namespace {

using Clock = std::chrono::steady_clock;
using namespace std::chrono_literals;

class VideoSendError : public std::runtime_error {
public:
  using std::runtime_error::runtime_error;
};

struct Shutdown {
  const std::atomic_bool& stopping;
  std::stop_token token;
  bool requested() const { return stopping.load() || token.stop_requested(); }
};

void writeBytes(SOCKET socket, std::span<const std::uint8_t> bytes,
                Clock::time_point deadline, const Shutdown& shutdown) {
  std::size_t offset = 0;
  while (offset < bytes.size()) {
    if (shutdown.requested()) throw VideoSendError("Video stopped");
    if (Clock::now() > deadline) throw VideoSendError("Video send exceeded 100 ms; reconnect for a fresh IDR");
    const auto written = send(socket, reinterpret_cast<const char*>(bytes.data() + offset),
      static_cast<int>(bytes.size() - offset), 0);
    if (written > 0) {
      offset += static_cast<std::size_t>(written);
      continue;
    }
    if (written == 0 || WSAGetLastError() != WSAEWOULDBLOCK) throw VideoSendError(socketError("video send").what());
    fd_set writable;
    FD_ZERO(&writable);
    FD_SET(socket, &writable);
    timeval timeout{0, 5000};
    if (select(0, nullptr, &writable, nullptr, &timeout) == SOCKET_ERROR) throw VideoSendError(socketError("video select").what());
  }
}

void writePacket(SOCKET socket, video::Header header, std::span<const std::uint8_t> payload,
                 const Shutdown& shutdown) {
  header.payloadSize = static_cast<std::uint32_t>(payload.size());
  header.sendTimestamp = performanceNanoseconds();
  const auto bytes = video::encodeHeader(header);
  const auto deadline = Clock::now() + 100ms;
  writeBytes(socket, bytes, deadline, shutdown);
  writeBytes(socket, payload, deadline, shutdown);
}

void stream(SOCKET socket, HWND window, const GraphicsDevice& graphics,
            const VideoOptions& options, const Shutdown& shutdown) {
  HardwareEncoder encoder(graphics, options);
  FrameConverter converter(graphics, options);
  WindowCapture capture(window, graphics);
  FrameWait wake;
  video::Header header;
  header.width = options.width;
  header.height = options.height;
  header.fps = options.fps;
  header.bitrate = options.bitrate;
  bool configured = false;
  bool warmed = false;
  std::uint64_t nextCapture = 0;
  std::uint64_t lastEncoded = 0;
  std::uint64_t frameCount = 0;
  const auto started = Clock::now();
  auto lastOutput = started;
  while (!shutdown.requested()) {
    char unexpected = 0;
    const auto received = recv(socket, &unexpected, 1, MSG_PEEK);
    if (received == 0) break;
    if (received > 0) throw std::runtime_error("Unexpected data on receive-only video socket");
    if (WSAGetLastError() != WSAEWOULDBLOCK) throw socketError("video peer");
    for (auto& frame : encoder.poll()) {
      if (frame.captureTimestamp <= lastEncoded) throw std::runtime_error("Encoder reordered a frame");
      lastEncoded = frame.captureTimestamp;
      const auto age = performanceNanoseconds() - frame.captureTimestamp;
      if ((warmed || Clock::now() - started > 2s) && age > 250'000'000) {
        throw std::runtime_error("Video frame age " + std::to_string(age / 1'000'000) + " ms exceeded limit; restarting stream");
      }
      if (age < 100'000'000) warmed = true;
      if (!configured) {
        auto parameters = encoder.parameterSets();
        if (parameters.empty()) parameters = video::extractParameterSets(frame.bytes);
        if (!frame.keyFrame || !video::hasNal(parameters, 7) || !video::hasNal(parameters, 8)) {
          throw std::runtime_error("Encoder did not start with an IDR and SPS/PPS");
        }
        header.type = video::Type::config;
        writePacket(socket, header, parameters, shutdown);
        configured = true;
      }
      header.type = video::Type::frame;
      header.keyFrame = frame.keyFrame;
      ++header.sequence;
      header.captureTimestamp = frame.captureTimestamp;
      header.encodeTimestamp = frame.encodeTimestamp;
      header.presentationUs = frame.captureTimestamp / 1000;
      writePacket(socket, header, frame.bytes, shutdown);
      ++frameCount;
      lastOutput = Clock::now();
    }
    if (encoder.canAccept()) {
      auto frame = capture.takeLatest();
      if (frame.owner) {
        // WGC follows source updates; rate-limit before encoding, never drop a dependent H.264 packet.
        const auto interval = 1'000'000'000ULL / options.fps;
        if (nextCapture == 0 || frame.timestamp + interval / 4 >= nextCapture) {
          const auto texture = converter.convert(frame);
          encoder.submit(texture.get(), frame.timestamp);
          nextCapture = nextCapture == 0 || frame.timestamp > nextCapture + interval
            ? frame.timestamp + interval : nextCapture + interval;
        }
        frame.owner.Close();
      }
    }
    if (encoder.pending() > 0 && Clock::now() - lastOutput > 2s) {
      throw std::runtime_error("Hardware encoder stalled");
    }
    if (!configured && Clock::now() - started > 5s) throw std::runtime_error("No captured video frame within 5 seconds");
    wake.wait();
  }
  const auto seconds = std::chrono::duration<double>(Clock::now() - started).count();
  std::cout << "Video session: " << frameCount << " frames (" << frameCount / seconds << " fps), "
    << capture.received() << " WGC frames (" << capture.received() / seconds << " fps), "
    << capture.dropped() << " capture replacements" << std::endl;
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
      listen(listener.get(), 1) != 0) throw socketError("video bind/listen");
  listener.nonblocking();
  std::cout << "Video listening on 127.0.0.1:" << options.port << " ("
    << options.width << 'x' << options.height << '@' << options.fps << ")" << std::endl;
  while (!shutdown.requested()) {
    fd_set readable;
    FD_ZERO(&readable);
    FD_SET(listener.get(), &readable);
    timeval timeout{0, 20000};
    const auto ready = select(0, &readable, nullptr, nullptr, &timeout);
    if (ready == SOCKET_ERROR) throw socketError("video listener select");
    if (!ready) continue;
    Socket client(accept(listener.get(), nullptr, nullptr));
    client.nonblocking();
    const BOOL noDelay = TRUE;
    const int sendBuffer = 32768;
    if (setsockopt(client.get(), IPPROTO_TCP, TCP_NODELAY, reinterpret_cast<const char*>(&noDelay), sizeof(noDelay)) ||
        setsockopt(client.get(), SOL_SOCKET, SO_SNDBUF, reinterpret_cast<const char*>(&sendBuffer), sizeof(sendBuffer))) {
      throw socketError("video socket options");
    }
    std::string failure;
    bool canSendDiagnostic = true;
    try {
      stream(client.get(), window, graphics, options, shutdown);
    } catch (const VideoSendError& error) {
      failure = error.what();
      canSendDiagnostic = false;
    } catch (const winrt::hresult_error& error) {
      failure = winrt::to_string(error.message());
    } catch (const std::exception& error) {
      failure = error.what();
    }
    if (!failure.empty() && !shutdown.requested()) {
      std::cerr << "Video session: " << failure << std::endl;
      if (!canSendDiagnostic) continue;
      try {
        video::Header header;
        header.type = video::Type::error;
        writePacket(client.get(), header, {reinterpret_cast<const std::uint8_t*>(failure.data()), failure.size()}, shutdown);
      } catch (...) {
        // A failed peer cannot receive the diagnostic; it remains available on the host console.
      }
    }
  }
}

}

void runVideoServer(VideoOptions options, HWND window, const std::atomic_bool& stopping,
                    std::stop_token token) noexcept {
  try {
    serve(options, window, {stopping, token});
  } catch (const winrt::hresult_error& error) {
    std::cerr << "Video unavailable: " << winrt::to_string(error.message()) << std::endl;
  } catch (const std::exception& error) {
    std::cerr << "Video unavailable: " << error.what() << std::endl;
  }
}

}
