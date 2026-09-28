#pragma once

#include "windows/capture/window-capture.h"
#include "windows/video/video-options.h"

namespace ift {

class FrameConverter {
public:
  FrameConverter(const GraphicsDevice& graphics, VideoOptions options);
  winrt::com_ptr<ID3D11Texture2D> convert(const CapturedFrame& frame);

private:
  void configure(UINT width, UINT height);
  const GraphicsDevice& graphics_;
  VideoOptions options_;
  UINT inputWidth_ = 0;
  UINT inputHeight_ = 0;
  winrt::com_ptr<ID3D11VideoDevice> videoDevice_;
  winrt::com_ptr<ID3D11VideoContext> videoContext_;
  winrt::com_ptr<ID3D11VideoProcessorEnumerator> enumerator_;
  winrt::com_ptr<ID3D11VideoProcessor> processor_;
};

}
