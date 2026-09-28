#include "windows/video/media-runtime.h"
#include "windows/video/video-options.h"

#include <d3d10.h>
#include <dxgi.h>
#include <mferror.h>
#include <mftransform.h>
#include <winrt/Windows.Graphics.Capture.h>

#include <iostream>
#include <stdexcept>

namespace ift {

std::uint64_t performanceNanoseconds() {
  static const auto frequency = [] {
    LARGE_INTEGER value{};
    QueryPerformanceFrequency(&value);
    return static_cast<std::uint64_t>(value.QuadPart);
  }();
  LARGE_INTEGER value{};
  QueryPerformanceCounter(&value);
  const auto count = static_cast<std::uint64_t>(value.QuadPart);
  return count / frequency * 1'000'000'000 + count % frequency * 1'000'000'000 / frequency;
}

MediaRuntime::MediaRuntime() {
  winrt::init_apartment(winrt::apartment_type::multi_threaded);
  const auto result = MFStartup(MF_VERSION, MFSTARTUP_FULL);
  if (FAILED(result)) {
    winrt::uninit_apartment();
    winrt::check_hresult(result);
  }
}

MediaRuntime::~MediaRuntime() {
  MFShutdown();
  winrt::uninit_apartment();
}

GraphicsDevice createGraphicsDevice() {
  GraphicsDevice graphics;
  const D3D_FEATURE_LEVEL levels[] = {D3D_FEATURE_LEVEL_11_1, D3D_FEATURE_LEVEL_11_0};
  winrt::check_hresult(D3D11CreateDevice(nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr,
    D3D11_CREATE_DEVICE_BGRA_SUPPORT | D3D11_CREATE_DEVICE_VIDEO_SUPPORT,
    levels, ARRAYSIZE(levels), D3D11_SDK_VERSION, graphics.device.put(), nullptr,
    graphics.context.put()));
  const auto dxgiDevice = graphics.device.as<IDXGIDevice>();
  winrt::com_ptr<IDXGIAdapter> adapter;
  winrt::check_hresult(dxgiDevice->GetAdapter(adapter.put()));
  DXGI_ADAPTER_DESC description{};
  winrt::check_hresult(adapter->GetDesc(&description));
  graphics.adapterId = description.AdapterLuid;
  graphics.name = description.Description;
  const auto multithread = graphics.context.as<ID3D10Multithread>();
  multithread->SetMultithreadProtected(TRUE);
  return graphics;
}

winrt::com_ptr<IMFActivate> findHardwareEncoder(const GraphicsDevice& graphics) {
  winrt::com_ptr<IMFAttributes> attributes;
  winrt::check_hresult(MFCreateAttributes(attributes.put(), 1));
  winrt::check_hresult(attributes->SetBlob(MFT_ENUM_ADAPTER_LUID,
    reinterpret_cast<const UINT8*>(&graphics.adapterId), sizeof(LUID)));
  const MFT_REGISTER_TYPE_INFO input{MFMediaType_Video, MFVideoFormat_NV12};
  const MFT_REGISTER_TYPE_INFO output{MFMediaType_Video, MFVideoFormat_H264};
  IMFActivate** activations = nullptr;
  UINT32 count = 0;
  winrt::check_hresult(MFTEnum2(MFT_CATEGORY_VIDEO_ENCODER,
    MFT_ENUM_FLAG_HARDWARE | MFT_ENUM_FLAG_SORTANDFILTER, &input, &output,
    attributes.get(), &activations, &count));
  winrt::com_ptr<IMFActivate> selected;
  if (count > 0) selected.copy_from(activations[0]);
  for (UINT32 index = 0; index < count; ++index) activations[index]->Release();
  CoTaskMemFree(activations);
  if (!selected) {
    throw std::runtime_error("No hardware H.264 encoder found for the capture GPU");
  }
  return selected;
}

void printVideoDiagnostics() {
  MediaRuntime runtime;
  std::cout << "Windows Graphics Capture: "
    << (winrt::Windows::Graphics::Capture::GraphicsCaptureSession::IsSupported()
      ? "supported" : "unavailable") << '\n';
  const auto graphics = createGraphicsDevice();
  std::wcout << L"Capture GPU: " << graphics.name << L'\n';
  const auto activation = findHardwareEncoder(graphics);
  wchar_t name[256]{};
  winrt::check_hresult(activation->GetString(MFT_FRIENDLY_NAME_Attribute,
    name, ARRAYSIZE(name), nullptr));
  std::wcout << L"Hardware H.264 encoder: " << name << L'\n';
  winrt::com_ptr<IMFTransform> transform;
  winrt::check_hresult(activation->ActivateObject(IID_PPV_ARGS(transform.put())));
  winrt::com_ptr<IMFAttributes> attributes;
  winrt::check_hresult(transform->GetAttributes(attributes.put()));
  UINT32 asynchronous = 0;
  attributes->GetUINT32(MF_TRANSFORM_ASYNC, &asynchronous);
  winrt::check_hresult(attributes->SetUINT32(MF_TRANSFORM_ASYNC_UNLOCK, TRUE));
  UINT32 aware = 0;
  attributes->GetUINT32(MF_SA_D3D11_AWARE, &aware);
  std::cout << "Async transform: " << asynchronous << "; D3D11 aware: " << aware << '\n';
  transform = nullptr;
  winrt::check_hresult(activation->ShutdownObject());
}

}
