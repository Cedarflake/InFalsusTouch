# Platform references

- [Official In Falsus Steam description](https://store.steampowered.com/app/3971950/In_Falsus/?l=schinese): keyboard lower notes and mouse upper Field; local 1.0.4b tutorial confirms the central ASDF and side Shift/Space layout.
- [Official In Falsus site](https://infalsus.lowiro.com/en-us/): aerial Field and rhythm gameplay.
- [Unity Input System Key](https://docs.unity3d.com/Packages/com.unity.inputsystem@1.11/api/UnityEngine.InputSystem.Key.html): physical keyboard positions use the US reference layout.
- [Unity Keyboard source, 1.11.2](https://github.com/Unity-Technologies/InputSystem/blob/1.11.2/Packages/com.unity.inputsystem/InputSystem/Devices/Keyboard.cs): numeric Key identifiers used by the observed IF preferences.
- [Flutter Android embedding](https://docs.flutter.dev/add-to-app/android/add-flutter-screen): FlutterActivity and engine lifecycle; implementation was also checked against the installed Flutter 3.44.9 SDK source.

- [Android multi-touch](https://developer.android.com/develop/ui/views/touch-and-input/gestures/multi): pointer IDs are stable, event indices can change.
- [MotionEvent](https://developer.android.com/reference/android/view/MotionEvent): action indices and native pointer coordinates.
- [SendInput](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-sendinput): injection results and privilege isolation.
- [MOUSEINPUT](https://learn.microsoft.com/en-us/windows/win32/api/winuser/ns-winuser-mouseinput): absolute virtual-desktop coordinates.
- [ADB](https://developer.android.com/tools/adb): authorized USB debugging, device selection and reverse forwarding.
- [AGP 8.9 compatibility](https://developer.android.com/build/releases/agp-8-9-0-release-notes): Gradle 8.11.1, SDK 35 and JDK requirements.
- [AndroidX Test releases](https://developer.android.com/jetpack/androidx/releases/test): pinned device-test dependencies.
- [WGC window capture interop](https://learn.microsoft.com/en-us/windows/win32/api/windows.graphics.capture.interop/nf-windows-graphics-capture-interop-igraphicscaptureiteminterop-createforwindow): explicit HWND capture.
- [WGC minimum update interval](https://learn.microsoft.com/en-us/uwp/api/windows.graphics.capture.graphicscapturesession.minupdateinterval?view=winrt-26100): optional OS pacing control.
- [MFTEnum2](https://learn.microsoft.com/en-us/windows/win32/api/mfapi/nf-mfapi-mftenum2): hardware encoder enumeration with adapter LUID stored as a blob.
- [Asynchronous MFTs](https://learn.microsoft.com/en-us/windows/win32/medfound/asynchronous-mfts): event credits, input/output rules and shutdown.
- [H.264 encoder](https://learn.microsoft.com/en-us/windows/win32/medfound/h-264-video-encoder): media types and codec controls.
- [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec): codec-specific data, Surface decoding, flush and presentation callbacks.
- [Android low-latency decoding](https://developer.android.com/about/versions/11/features): feature detection and codec configuration.
- [Android frame rate](https://developer.android.com/media/optimize/performance/frame-rate): separate window/video Surface hints, actual display modes and system policy.
- [ImmGetVirtualKey](https://learn.microsoft.com/en-us/windows/win32/api/imm/nf-imm-immgetvirtualkey): IMEs can replace key messages with VK_PROCESSKEY; a physical scan-code test must account for the active keyboard layout.
