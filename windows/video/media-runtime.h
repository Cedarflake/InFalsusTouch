#pragma once

#include <d3d11.h>
#include <mfapi.h>
#include <mfidl.h>
#include <winrt/base.h>

#include <string>

namespace ift {

class MediaRuntime {
public:
  MediaRuntime();
  ~MediaRuntime();
  MediaRuntime(const MediaRuntime&) = delete;
  MediaRuntime& operator=(const MediaRuntime&) = delete;
};

struct GraphicsDevice {
  winrt::com_ptr<ID3D11Device> device;
  winrt::com_ptr<ID3D11DeviceContext> context;
  LUID adapterId{};
  std::wstring name;
};

GraphicsDevice createGraphicsDevice();
winrt::com_ptr<IMFActivate> findHardwareEncoder(const GraphicsDevice& graphics);
void printVideoDiagnostics();

}
