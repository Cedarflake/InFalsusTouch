#include <Windows.h>
#include <d3d11_1.h>
#include <dxgi.h>
#include <winrt/base.h>

#include <chrono>
#include <iostream>
#include <string_view>

namespace {

LRESULT CALLBACK windowProcedure(HWND window, UINT message, WPARAM parameter, LPARAM data) {
  if (message == WM_DESTROY) { PostQuitMessage(0); return 0; }
  if (message == WM_PAINT) {
    PAINTSTRUCT paint{};
    BeginPaint(window, &paint);
    EndPaint(window, &paint);
    return 0;
  }
  return DefWindowProcW(window, message, parameter, data);
}

void render(HWND window, LONG width, LONG height) {
  DXGI_SWAP_CHAIN_DESC swap{};
  swap.BufferDesc.Width = width;
  swap.BufferDesc.Height = height;
  swap.BufferDesc.Format = DXGI_FORMAT_B8G8R8A8_UNORM;
  swap.SampleDesc.Count = 1;
  swap.BufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
  swap.BufferCount = 2;
  swap.OutputWindow = window;
  swap.Windowed = TRUE;
  swap.SwapEffect = DXGI_SWAP_EFFECT_FLIP_DISCARD;
  winrt::com_ptr<IDXGISwapChain> chain;
  winrt::com_ptr<ID3D11Device> device;
  winrt::com_ptr<ID3D11DeviceContext> context;
  winrt::check_hresult(D3D11CreateDeviceAndSwapChain(nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr,
    D3D11_CREATE_DEVICE_BGRA_SUPPORT, nullptr, 0, D3D11_SDK_VERSION, &swap, chain.put(),
    device.put(), nullptr, context.put()));
  const auto context1 = context.as<ID3D11DeviceContext1>();
  winrt::com_ptr<ID3D11Texture2D> buffer;
  winrt::check_hresult(chain->GetBuffer(0, IID_PPV_ARGS(buffer.put())));
  winrt::com_ptr<ID3D11RenderTargetView> target;
  winrt::check_hresult(device->CreateRenderTargetView(buffer.get(), nullptr, target.put()));
  const float colors[][4] = {
    {235.f / 255, 60.f / 255, 60.f / 255, 1}, {60.f / 255, 210.f / 255, 90.f / 255, 1},
    {60.f / 255, 90.f / 255, 235.f / 255, 1}, {230.f / 255, 205.f / 255, 55.f / 255, 1},
    {200.f / 255, 70.f / 255, 200.f / 255, 1}, {60.f / 255, 200.f / 255, 210.f / 255, 1},
  };
  const float white[]{1, 1, 1, 1};
  unsigned frames = 0;
  const auto started = std::chrono::steady_clock::now();
  MSG message{};
  for (;;) {
    while (PeekMessageW(&message, nullptr, 0, 0, PM_REMOVE)) {
      if (message.message == WM_QUIT) return;
      TranslateMessage(&message);
      DispatchMessageW(&message);
    }
    for (LONG lane = 0; lane < 6; ++lane) {
      const D3D11_RECT rectangle{lane * width / 6, 0, (lane + 1) * width / 6, height};
      context1->ClearView(target.get(), colors[lane], &rectangle, 1);
    }
    const auto elapsed = std::chrono::duration<double>(std::chrono::steady_clock::now() - started).count();
    const LONG x = static_cast<LONG>(elapsed * 400) % width;
    const D3D11_RECT cursor{x, height * 100 / 720, x + 12, height * 640 / 720};
    context1->ClearView(target.get(), white, &cursor, 1);
    const auto result = chain->Present(1, 0);
    winrt::check_hresult(result);
    if (result == DXGI_STATUS_OCCLUDED) Sleep(10);
    if (++frames % 240 == 0) std::cout << "Pattern source: " << frames / elapsed << " fps" << std::endl;
  }
}

}

int main(int argc, char** argv) {
  try {
    const std::string_view resolution = argc == 2 ? argv[1] : "720p";
    if (argc > 2 || (resolution != "720p" && resolution != "1080p")) {
      std::cerr << "Usage: ift_video_pattern.exe [720p|1080p]\n";
      return 1;
    }
    const LONG width = resolution == "1080p" ? 1920 : 1280;
    const LONG height = resolution == "1080p" ? 1080 : 720;
    SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
    const auto instance = GetModuleHandleW(nullptr);
    WNDCLASSW windowClass{};
    windowClass.hInstance = instance;
    windowClass.lpfnWndProc = windowProcedure;
    windowClass.lpszClassName = L"InFalsusTouchVideoTest";
    if (!RegisterClassW(&windowClass)) return 1;
    constexpr DWORD style = WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX;
    RECT rect{0, 0, width, height};
    AdjustWindowRect(&rect, style, FALSE);
    const auto window = CreateWindowExW(0, windowClass.lpszClassName, L"InFalsusTouch Direct3D video test",
      style, 30, 30, rect.right - rect.left, rect.bottom - rect.top, nullptr, nullptr, instance, nullptr);
    if (!window) return 1;
    ShowWindow(window, SW_SHOWNOACTIVATE);
    RECT client{};
    if (!GetClientRect(window, &client) || client.right != width || client.bottom != height) return 1;
    std::cout << "0x" << std::hex << reinterpret_cast<std::uintptr_t>(window) << std::dec << std::endl;
    std::cout << "Pattern client: " << client.right << 'x' << client.bottom << std::endl;
    render(window, width, height);
    return 0;
  } catch (const winrt::hresult_error& error) {
    std::cerr << winrt::to_string(error.message()) << '\n';
    return 1;
  }
}
