#include "windows/video/encoding-group.h"

#include <chrono>
#include <iostream>
#include <stdexcept>
#include <syncstream>

#include "protocol/cpp/video-packet.h"
#include "windows/encoder/hardware-encoder.h"
#include "windows/video/frame-wait.h"

namespace ift {

EncodingGroup::EncodingGroup(GraphicsDevice graphics, VideoOptions options)
  : worker_([this, graphics = std::move(graphics), options](std::stop_token token) { run(graphics, options, token); }) {}

EncodingGroup::~EncodingGroup() {
  stop();
  worker_.join();
}

void EncodingGroup::submit(winrt::com_ptr<ID3D11Texture2D> texture, std::uint64_t timestamp) {
  {
    std::lock_guard lock(mutex_);
    std::swap(latest_, texture);
    timestamp_ = timestamp;
  }
  frameAvailable_.notify_one();
}

GroupOutput EncodingGroup::poll() {
  std::lock_guard lock(mutex_);
  GroupOutput output;
  output.frames.swap(output_.frames);
  output.failure = output_.failure;
  return output;
}

void EncodingGroup::run(const GraphicsDevice& graphics, VideoOptions options, std::stop_token token) noexcept {
  using Clock = std::chrono::steady_clock;
  using namespace std::chrono_literals;
  const auto started = Clock::now();
  std::uint64_t frames = 0;
  std::string failure;
  try {
    MediaRuntime runtime;
    HardwareEncoder encoder(graphics, options);
    VideoPayload parameters;
    FrameWait wake;
    std::uint64_t lastEncoded = 0;
    auto lastOutput = Clock::now();
    auto awaitingVideoSince = lastOutput;
    bool warmed = false;
    std::osyncstream(std::cout) << "Video group " << options.fps << " FPS ready" << std::endl;
    while (!token.stop_requested()) {
      for (auto& frame : encoder.poll()) {
        if (frame.captureTimestamp <= lastEncoded) throw std::runtime_error("Encoder reordered a frame");
        lastEncoded = frame.captureTimestamp;
        const auto age = performanceNanoseconds() - frame.captureTimestamp;
        if ((warmed || Clock::now() - started > 2s) && age > 250'000'000) {
          throw std::runtime_error("Encoded frame exceeded 250 ms");
        }
        if (age < 100'000'000) warmed = true;
        if (!parameters) {
          auto bytes = encoder.parameterSets();
          if (bytes.empty()) bytes = video::extractParameterSets(frame.bytes);
          if (!frame.keyFrame || !video::hasNal(bytes, 7) || !video::hasNal(bytes, 8)) {
            throw std::runtime_error("Encoder did not start with an IDR and SPS/PPS");
          }
          parameters = std::make_shared<const std::vector<std::uint8_t>>(std::move(bytes));
        }
        GroupFrame output{std::make_shared<const std::vector<std::uint8_t>>(std::move(frame.bytes)), parameters,
          frame.captureTimestamp, frame.encodeTimestamp, frame.keyFrame};
        {
          std::lock_guard lock(mutex_);
          if (output_.frames.size() >= 3) throw std::runtime_error("Encoded group exceeded bounded output queue");
          output_.frames.push_back(std::move(output));
        }
        ++frames;
        lastOutput = Clock::now();
      }
      if (encoder.canAccept()) {
        winrt::com_ptr<ID3D11Texture2D> texture;
        std::uint64_t timestamp = 0;
        {
          std::lock_guard lock(mutex_);
          texture = std::move(latest_);
          timestamp = timestamp_;
        }
        if (texture) {
          if (!encoder.pending()) lastOutput = Clock::now();
          encoder.submit(texture.get(), timestamp);
        }
      }
      if (encoder.pending() && Clock::now() - lastOutput > 2s) throw std::runtime_error("Hardware encoder stalled");
      if (!parameters && Clock::now() - awaitingVideoSince > 5s) throw std::runtime_error("No encoded video within 5 seconds");
      if (!encoder.pending()) {
        std::unique_lock lock(mutex_);
        if (!latest_) {
          frameAvailable_.wait(lock, token, [this] { return latest_ != nullptr; });
          awaitingVideoSince = lastOutput = Clock::now();
          continue;
        }
      }
      wake.wait();
    }
  } catch (const winrt::hresult_error& error) {
    failure = winrt::to_string(error.message());
  } catch (const std::exception& error) {
    failure = error.what();
  }
  {
    std::lock_guard lock(mutex_);
    latest_ = nullptr;
    output_.failure = std::move(failure);
  }
  std::osyncstream(std::cout) << "Video group " << options.fps << " FPS stopped: " << frames << " encoded frames" << std::endl;
  stopped_.store(true);
}

}
