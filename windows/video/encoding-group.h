#pragma once

#include <atomic>
#include <condition_variable>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <vector>

#include "windows/video/media-runtime.h"
#include "windows/video/video-options.h"

namespace ift {

using VideoPayload = std::shared_ptr<const std::vector<std::uint8_t>>;

struct GroupFrame {
  VideoPayload payload;
  VideoPayload parameters;
  std::uint64_t captureTimestamp;
  std::uint64_t encodeTimestamp;
  bool keyFrame;
};

struct GroupOutput {
  std::vector<GroupFrame> frames;
  std::string failure;
};

class EncodingGroup {
public:
  EncodingGroup(GraphicsDevice graphics, VideoOptions options);
  ~EncodingGroup();
  void submit(winrt::com_ptr<ID3D11Texture2D> texture, std::uint64_t timestamp);
  GroupOutput poll();
  void stop() { worker_.request_stop(); }
  bool stopped() const { return stopped_.load(); }

private:
  void run(const GraphicsDevice& graphics, VideoOptions options, std::stop_token token) noexcept;
  std::mutex mutex_;
  std::condition_variable_any frameAvailable_;
  winrt::com_ptr<ID3D11Texture2D> latest_;
  std::uint64_t timestamp_ = 0;
  GroupOutput output_;
  std::atomic_bool stopped_ = false;
  std::jthread worker_;
};

}
