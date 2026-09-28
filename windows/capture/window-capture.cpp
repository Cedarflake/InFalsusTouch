#include "windows/capture/window-capture.h"
#include "windows/video/video-options.h"

#include <dwmapi.h>
#include <dxgi.h>
#include <windows.graphics.capture.interop.h>
#include <windows.graphics.directx.direct3d11.interop.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Foundation.Metadata.h>
#include <winrt/Windows.Graphics.DirectX.h>

#include <algorithm>
#include <atomic>
#include <mutex>
#include <iostream>
#include <stdexcept>

namespace ift {
using namespace winrt::Windows::Graphics;
using namespace winrt::Windows::Graphics::Capture;
namespace WinrtDirectX = winrt::Windows::Graphics::DirectX;

namespace {

template <typename Resource>
void closeResource(Resource& resource) noexcept {
  if (!resource) return;
  try { resource.Close(); } catch (...) { }
  resource = nullptr;
}

}

struct WindowCapture::State {
  std::mutex mutex;
  Direct3D11CaptureFrame latest{nullptr};
  std::uint64_t timestamp = 0;
  std::atomic_uint64_t dropped{0};
  std::atomic_uint64_t received{0};
  bool stopping = false;
  HRESULT failure = S_OK;
};

WindowCapture::WindowCapture(HWND window, const GraphicsDevice& graphics)
  : window_(window), state_(std::make_shared<State>()) {
  try {
    initialize(graphics);
  } catch (...) {
    stop();
    throw;
  }
}

void WindowCapture::initialize(const GraphicsDevice& graphics) {
  if (!GraphicsCaptureSession::IsSupported()) {
    throw std::runtime_error("Windows Graphics Capture is unavailable");
  }
  GetWindowThreadProcessId(window_, &processId_);
  const auto factory = winrt::get_activation_factory<GraphicsCaptureItem, IGraphicsCaptureItemInterop>();
  winrt::check_hresult(factory->CreateForWindow(window_, winrt::guid_of<GraphicsCaptureItem>(),
    winrt::put_abi(item_)));
  const auto dxgi = graphics.device.as<IDXGIDevice>();
  winrt::com_ptr<IInspectable> inspectable;
  winrt::check_hresult(CreateDirect3D11DeviceFromDXGIDevice(dxgi.get(), inspectable.put()));
  device_ = inspectable.as<WinrtDirectX::Direct3D11::IDirect3DDevice>();
  size_ = item_.Size();
  pool_ = Direct3D11CaptureFramePool::CreateFreeThreaded(device_,
    WinrtDirectX::DirectXPixelFormat::B8G8R8A8UIntNormalized, 2, size_);
  const auto state = state_;
  frameToken_ = pool_.FrameArrived([state](const auto& pool, const auto&) noexcept {
    std::lock_guard lock(state->mutex);
    if (state->stopping) return;
    try {
      auto frame = pool.TryGetNextFrame();
      if (frame) {
        ++state->received;
        state->timestamp = performanceNanoseconds();
        if (state->latest) {
          state->latest.Close();
          ++state->dropped;
        }
        state->latest = std::move(frame);
      }
    } catch (...) {
      state->failure = winrt::to_hresult();
    }
  });
  session_ = pool_.CreateCaptureSession(item_);
  session_.IsCursorCaptureEnabled(false);
  if (winrt::Windows::Foundation::Metadata::ApiInformation::IsPropertyPresent(
      L"Windows.Graphics.Capture.GraphicsCaptureSession", L"MinUpdateInterval")) {
    const auto previous = session_.MinUpdateInterval().count();
    session_.MinUpdateInterval(winrt::Windows::Foundation::TimeSpan{0});
    std::cout << "WGC minimum interval: " << previous << " -> 0 (100 ns units); host pacing enabled" << std::endl;
  }
  session_.StartCapture();
}

WindowCapture::~WindowCapture() { stop(); }

void WindowCapture::stop() noexcept {
  {
    std::lock_guard lock(state_->mutex);
    state_->stopping = true;
    closeResource(state_->latest);
  }
  if (pool_) {
    try { pool_.FrameArrived(frameToken_); } catch (...) { }
  }
  closeResource(session_);
  closeResource(pool_);
}

CapturedFrame WindowCapture::takeLatest() {
  DWORD currentProcess = 0;
  GetWindowThreadProcessId(window_, &currentProcess);
  if (currentProcess == 0 || currentProcess != processId_) {
    throw std::runtime_error("Capture window closed");
  }
  CapturedFrame result;
  {
    std::lock_guard lock(state_->mutex);
    winrt::check_hresult(state_->failure);
    result.owner = std::exchange(state_->latest, nullptr);
    result.timestamp = state_->timestamp;
  }
  if (!result.owner) return result;
  const auto contentSize = result.owner.ContentSize();
  if (contentSize.Width != size_.Width || contentSize.Height != size_.Height) {
    result.owner.Close();
    result.owner = nullptr;
    if (contentSize.Width > 0 && contentSize.Height > 0) {
      size_ = contentSize;
      pool_.Recreate(device_, WinrtDirectX::DirectXPixelFormat::B8G8R8A8UIntNormalized, 2, size_);
    }
    ++state_->dropped;
    return result;
  }
  if (IsIconic(window_)) {
    result.owner.Close();
    result.owner = nullptr;
    return result;
  }
  const auto access = result.owner.Surface().as<Windows::Graphics::DirectX::Direct3D11::IDirect3DDxgiInterfaceAccess>();
  winrt::check_hresult(access->GetInterface(IID_PPV_ARGS(result.texture.put())));
  RECT bounds{};
  winrt::check_hresult(DwmGetWindowAttribute(window_, DWMWA_EXTENDED_FRAME_BOUNDS, &bounds, sizeof(bounds)));
  RECT client{};
  POINT origin{};
  if (!GetClientRect(window_, &client) || !ClientToScreen(window_, &origin)) {
    throw std::runtime_error("Cannot read capture window client area");
  }
  result.client = {
    std::clamp(origin.x - bounds.left, 0L, static_cast<LONG>(size_.Width)),
    std::clamp(origin.y - bounds.top, 0L, static_cast<LONG>(size_.Height)),
    std::clamp(origin.x - bounds.left + client.right, 0L, static_cast<LONG>(size_.Width)),
    std::clamp(origin.y - bounds.top + client.bottom, 0L, static_cast<LONG>(size_.Height)),
  };
  return result;
}

std::uint64_t WindowCapture::dropped() const { return state_->dropped.load(); }
std::uint64_t WindowCapture::received() const { return state_->received.load(); }

}
