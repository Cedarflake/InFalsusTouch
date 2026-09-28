#pragma once

#include <winrt/Windows.Graphics.Capture.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Graphics.DirectX.Direct3D11.h>

#include <memory>

#include "windows/video/media-runtime.h"

namespace ift {

struct CapturedFrame {
  winrt::Windows::Graphics::Capture::Direct3D11CaptureFrame owner{nullptr};
  winrt::com_ptr<ID3D11Texture2D> texture;
  RECT client{};
  std::uint64_t timestamp = 0;
};

class WindowCapture {
public:
  WindowCapture(HWND window, const GraphicsDevice& graphics);
  ~WindowCapture();
  WindowCapture(const WindowCapture&) = delete;
  WindowCapture& operator=(const WindowCapture&) = delete;
  CapturedFrame takeLatest();
  std::uint64_t dropped() const;
  std::uint64_t received() const;

private:
  void initialize(const GraphicsDevice& graphics);
  void stop() noexcept;
  struct State;
  HWND window_;
  DWORD processId_ = 0;
  std::shared_ptr<State> state_;
  winrt::Windows::Graphics::DirectX::Direct3D11::IDirect3DDevice device_{nullptr};
  winrt::Windows::Graphics::Capture::GraphicsCaptureItem item_{nullptr};
  winrt::Windows::Graphics::Capture::Direct3D11CaptureFramePool pool_{nullptr};
  winrt::Windows::Graphics::Capture::GraphicsCaptureSession session_{nullptr};
  winrt::Windows::Graphics::SizeInt32 size_{};
  winrt::event_token frameToken_{};
};

}
