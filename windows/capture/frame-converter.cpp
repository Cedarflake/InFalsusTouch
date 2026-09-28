#include "windows/capture/frame-converter.h"

#include <algorithm>
#include <stdexcept>

namespace ift {

FrameConverter::FrameConverter(const GraphicsDevice& graphics, VideoOptions options)
  : graphics_(graphics), options_(options), videoDevice_(graphics.device.as<ID3D11VideoDevice>()),
    videoContext_(graphics.context.as<ID3D11VideoContext>()) {}

void FrameConverter::configure(UINT width, UINT height) {
  D3D11_VIDEO_PROCESSOR_CONTENT_DESC description{};
  description.InputFrameFormat = D3D11_VIDEO_FRAME_FORMAT_PROGRESSIVE;
  description.InputFrameRate = {options_.fps, 1};
  description.InputWidth = width;
  description.InputHeight = height;
  description.OutputFrameRate = {options_.fps, 1};
  description.OutputWidth = options_.width;
  description.OutputHeight = options_.height;
  description.Usage = D3D11_VIDEO_USAGE_PLAYBACK_NORMAL;
  processor_ = nullptr;
  enumerator_ = nullptr;
  winrt::check_hresult(videoDevice_->CreateVideoProcessorEnumerator(&description, enumerator_.put()));
  UINT support = 0;
  winrt::check_hresult(enumerator_->CheckVideoProcessorFormat(DXGI_FORMAT_NV12, &support));
  if (!(support & D3D11_VIDEO_PROCESSOR_FORMAT_SUPPORT_OUTPUT)) {
    throw std::runtime_error("GPU cannot convert capture frames to NV12");
  }
  winrt::check_hresult(videoDevice_->CreateVideoProcessor(enumerator_.get(), 0, processor_.put()));
  videoContext_->VideoProcessorSetStreamFrameFormat(processor_.get(), 0, D3D11_VIDEO_FRAME_FORMAT_PROGRESSIVE);
  videoContext_->VideoProcessorSetStreamAutoProcessingMode(processor_.get(), 0, FALSE);
  D3D11_VIDEO_PROCESSOR_COLOR_SPACE inputColor{};
  inputColor.RGB_Range = 0;
  inputColor.YCbCr_Matrix = 1;
  D3D11_VIDEO_PROCESSOR_COLOR_SPACE outputColor{};
  outputColor.YCbCr_Matrix = 1;
  outputColor.Nominal_Range = 1;
  videoContext_->VideoProcessorSetStreamColorSpace(processor_.get(), 0, &inputColor);
  videoContext_->VideoProcessorSetOutputColorSpace(processor_.get(), &outputColor);
  D3D11_VIDEO_COLOR black{};
  black.YCbCr = {16.f / 255.f, 0.5f, 0.5f, 1.f};
  videoContext_->VideoProcessorSetOutputBackgroundColor(processor_.get(), TRUE, &black);
  inputWidth_ = width;
  inputHeight_ = height;
}

winrt::com_ptr<ID3D11Texture2D> FrameConverter::convert(const CapturedFrame& frame) {
  D3D11_TEXTURE2D_DESC source{};
  frame.texture->GetDesc(&source);
  if (source.Width != inputWidth_ || source.Height != inputHeight_) configure(source.Width, source.Height);
  const auto sourceWidth = frame.client.right - frame.client.left;
  const auto sourceHeight = frame.client.bottom - frame.client.top;
  if (sourceWidth <= 0 || sourceHeight <= 0) throw std::runtime_error("Capture client area is empty");
  const auto scale = std::min(static_cast<double>(options_.width) / sourceWidth,
    static_cast<double>(options_.height) / sourceHeight);
  const auto width = static_cast<LONG>(sourceWidth * scale) & ~1L;
  const auto height = static_cast<LONG>(sourceHeight * scale) & ~1L;
  const LONG left = (options_.width - width) / 2;
  const LONG top = (options_.height - height) / 2;
  const RECT destination{left, top, left + width, top + height};
  const RECT outputRect{0, 0, options_.width, options_.height};
  videoContext_->VideoProcessorSetStreamSourceRect(processor_.get(), 0, TRUE, &frame.client);
  videoContext_->VideoProcessorSetStreamDestRect(processor_.get(), 0, TRUE, &destination);
  videoContext_->VideoProcessorSetOutputTargetRect(processor_.get(), TRUE, &outputRect);

  D3D11_TEXTURE2D_DESC target{};
  target.Width = options_.width;
  target.Height = options_.height;
  target.MipLevels = 1;
  target.ArraySize = 1;
  target.Format = DXGI_FORMAT_NV12;
  target.SampleDesc.Count = 1;
  target.Usage = D3D11_USAGE_DEFAULT;
  target.BindFlags = D3D11_BIND_RENDER_TARGET;
  winrt::com_ptr<ID3D11Texture2D> texture;
  winrt::check_hresult(graphics_.device->CreateTexture2D(&target, nullptr, texture.put()));
  D3D11_VIDEO_PROCESSOR_INPUT_VIEW_DESC inputDesc{};
  inputDesc.ViewDimension = D3D11_VPIV_DIMENSION_TEXTURE2D;
  winrt::com_ptr<ID3D11VideoProcessorInputView> input;
  winrt::check_hresult(videoDevice_->CreateVideoProcessorInputView(frame.texture.get(), enumerator_.get(),
    &inputDesc, input.put()));
  D3D11_VIDEO_PROCESSOR_OUTPUT_VIEW_DESC outputDesc{};
  outputDesc.ViewDimension = D3D11_VPOV_DIMENSION_TEXTURE2D;
  winrt::com_ptr<ID3D11VideoProcessorOutputView> output;
  winrt::check_hresult(videoDevice_->CreateVideoProcessorOutputView(texture.get(), enumerator_.get(),
    &outputDesc, output.put()));
  D3D11_VIDEO_PROCESSOR_STREAM stream{};
  stream.Enable = TRUE;
  stream.pInputSurface = input.get();
  winrt::check_hresult(videoContext_->VideoProcessorBlt(processor_.get(), output.get(), 0, 1, &stream));
  graphics_.context->Flush();
  return texture;
}

}
