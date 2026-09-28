#pragma once

#include <mftransform.h>

#include <map>
#include <vector>

#include "windows/video/media-runtime.h"
#include "windows/video/video-options.h"

namespace ift {

struct EncodedFrame {
  std::vector<std::uint8_t> bytes;
  std::uint64_t captureTimestamp = 0;
  std::uint64_t encodeTimestamp = 0;
  bool keyFrame = false;
};

class HardwareEncoder {
public:
  HardwareEncoder(const GraphicsDevice& graphics, VideoOptions options);
  ~HardwareEncoder();
  HardwareEncoder(const HardwareEncoder&) = delete;
  HardwareEncoder& operator=(const HardwareEncoder&) = delete;
  bool canAccept() const;
  void submit(ID3D11Texture2D* texture, std::uint64_t timestamp);
  std::vector<EncodedFrame> poll();
  std::vector<std::uint8_t> parameterSets() const;
  std::size_t pending() const { return timestamps_.size(); }

private:
  void configure(const GraphicsDevice& graphics);
  EncodedFrame readOutput();
  VideoOptions options_;
  winrt::com_ptr<IMFActivate> activation_;
  winrt::com_ptr<IMFTransform> transform_;
  winrt::com_ptr<IMFMediaEventGenerator> events_;
  winrt::com_ptr<IMFDXGIDeviceManager> manager_;
  std::map<LONGLONG, std::uint64_t> timestamps_;
  DWORD inputId_ = 0;
  DWORD outputId_ = 0;
  std::uint32_t credits_ = 0;
  bool first_ = true;
};

}
