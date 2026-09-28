#include <Windows.h>
#include <d3d11_1.h>
#include <dxgi.h>
#include <winrt/base.h>

#include <algorithm>
#include <chrono>
#include <iostream>
#include <sstream>
#include <stdexcept>
#include <string>
#include <string_view>

namespace {

constexpr DWORD windowStyle = WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX;

bool processCommands(HWND window, std::string& pending) {
  const auto input = GetStdHandle(STD_INPUT_HANDLE);
  DWORD available = 0;
  if (!PeekNamedPipe(input, nullptr, 0, nullptr, &available, nullptr)) {
    if (GetLastError() == ERROR_BROKEN_PIPE) return false;
    throw std::runtime_error("Controlled pattern requires a stdin pipe");
  }
  if (available == 0) return true;
  char bytes[256];
  DWORD count = 0;
  if (!ReadFile(input, bytes, std::min<DWORD>(available, sizeof(bytes)), &count, nullptr)) {
    throw std::runtime_error("Cannot read pattern command");
  }
  pending.append(bytes, count);
  if (pending.size() > 1024) throw std::runtime_error("Pattern command is too long");
  for (auto end = pending.find('\n'); end != std::string::npos; end = pending.find('\n')) {
    const auto line = pending.substr(0, end);
    pending.erase(0, end + 1);
    std::istringstream command(line);
    std::string action;
    command >> action;
    if (action == "resize") {
      LONG width = 0;
      LONG height = 0;
      if (!(command >> width >> height) || width < 128 || width > 1920 || height < 128 || height > 1080) {
        throw std::runtime_error("Invalid pattern client size");
      }
      RECT rect{0, 0, width, height};
      winrt::check_bool(AdjustWindowRect(&rect, windowStyle, FALSE));
      winrt::check_bool(SetWindowPos(window, nullptr, 0, 0, rect.right - rect.left, rect.bottom - rect.top,
        SWP_NOMOVE | SWP_NOZORDER | SWP_NOACTIVATE));
    } else if (action == "minimize") {
      ShowWindow(window, SW_SHOWMINNOACTIVE);
    } else if (action == "restore") {
      ShowWindow(window, SW_SHOWNOACTIVATE);
    } else {
      throw std::runtime_error("Unknown pattern command");
    }
    std::string extra;
    if (command >> extra) throw std::runtime_error("Unexpected pattern command argument");
    RECT client{};
    winrt::check_bool(GetClientRect(window, &client));
    std::cout << "Pattern state: " << action << ' ' << client.right << ' ' << client.bottom
      << ' ' << (IsIconic(window) ? "minimized" : "visible") << std::endl;
  }
  return true;
}

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

void render(HWND window, LONG width, LONG height, bool controlled) {
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
  std::string pending;
  const auto started = std::chrono::steady_clock::now();
  MSG message{};
  for (;;) {
    while (PeekMessageW(&message, nullptr, 0, 0, PM_REMOVE)) {
      if (message.message == WM_QUIT) return;
      TranslateMessage(&message);
      DispatchMessageW(&message);
    }
    if (controlled && !processCommands(window, pending)) return;
    if (IsIconic(window)) {
      Sleep(10);
      continue;
    }
    RECT client{};
    winrt::check_bool(GetClientRect(window, &client));
    if (client.right != width || client.bottom != height) {
      target = nullptr;
      buffer = nullptr;
      winrt::check_hresult(chain->ResizeBuffers(0, client.right, client.bottom, DXGI_FORMAT_UNKNOWN, 0));
      winrt::check_hresult(chain->GetBuffer(0, IID_PPV_ARGS(buffer.put())));
      winrt::check_hresult(device->CreateRenderTargetView(buffer.get(), nullptr, target.put()));
      width = client.right;
      height = client.bottom;
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
    const std::string_view resolution = argc >= 2 ? argv[1] : "720p";
    const bool controlled = argc == 3 && std::string_view(argv[2]) == "--controlled";
    if (argc > 3 || (argc == 3 && !controlled) || (resolution != "720p" && resolution != "1080p")) {
      std::cerr << "Usage: ift_video_pattern.exe [720p|1080p] [--controlled]\n";
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
    RECT rect{0, 0, width, height};
    AdjustWindowRect(&rect, windowStyle, FALSE);
    const auto window = CreateWindowExW(0, windowClass.lpszClassName, L"InFalsusTouch Direct3D video test",
      windowStyle, 30, 30, rect.right - rect.left, rect.bottom - rect.top, nullptr, nullptr, instance, nullptr);
    if (!window) return 1;
    ShowWindow(window, SW_SHOWNOACTIVATE);
    if (controlled) {
      winrt::check_bool(SetWindowPos(window, HWND_BOTTOM, 0, 0, 0, 0,
        SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE));
    }
    RECT client{};
    if (!GetClientRect(window, &client) || client.right != width || client.bottom != height) return 1;
    std::cout << "0x" << std::hex << reinterpret_cast<std::uintptr_t>(window) << std::dec << std::endl;
    std::cout << "Pattern client: " << client.right << 'x' << client.bottom << std::endl;
    render(window, width, height, controlled);
    return 0;
  } catch (const winrt::hresult_error& error) {
    std::cerr << winrt::to_string(error.message()) << '\n';
    return 1;
  } catch (const std::exception& error) {
    std::cerr << error.what() << '\n';
    return 1;
  }
}
