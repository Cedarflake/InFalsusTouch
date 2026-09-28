# Implementation status

## Current milestone

Phase 1 input and Phase 2 hardware video prototypes are implemented and verified
on 2026-09-28. The connected Android 14 phone passed real USB H.264 decoding,
rendered color checks, seven synthetic pointers during playback and reconnect.
Bounded queues, frame pacing and local latency statistics provide the Phase 3
baseline. Physical gameplay and the Phase 4 settings/calibration UX remain open;
the full product goal is still active.

## Acceptance gates

| Gate | Status |
| --- | --- |
| Architecture and input protocol | Defined |
| Windows host compilation | Passed, MSVC 19.44 / CMake 3.31.6 / Windows SDK 10.0.26100.0 |
| Android APK compilation | Passed, Gradle 8.11.1 / AGP 8.9.2 / Kotlin 2.1.20 / JDK 21 |
| C++ input protocol / input state / mapping / video suites | 4/4 passed |
| Kotlin touch / input+video protocol / queue / socket / native-host tests | 22/22 passed, no skips |
| TCP disconnect / malformed / reconnect integration | 8/8 checks passed |
| Android lint | Passed, no issues found |
| Android 14 device MotionEvent + USB transport instrumentation | 4/4 passed |
| Android Activity launch and landscape screen inspection | Passed at 2400 x 1080 |
| Physical finger tracking and In Falsus gameplay | Not tested |
| WGC / GPU conversion / hardware H.264 / TCP | Passed, including Baseline SPS and >=58 FPS gate |
| Android hardware decode / actual output colors / concurrent seven-pointer input / reconnect | 1/1 device integration test passed |
| 720p60 short-run throughput | PC 60.15 FPS; USB phone steady receive 60.00 FPS, present callbacks 59.00 FPS |
| Measured latency tuning | Bounded queues, WGC pacing, low-latency codec selection and local statistics implemented |
| Full settings / calibration UX | Phase 4, not implemented |

## Reproduce

```powershell
.\scripts\build-windows.ps1
.\scripts\build-android.ps1 -DeviceTests
uv run --python 3.13 tests\tcp-integration.py --host dist\InFalsusTouchHost.exe
.\scripts\test-device.ps1 -SkipBuild
uv run --python 3.13 tests\video-integration.py --seconds 8 --min-fps 58
uv run --python 3.13 tests\video-device.py
```

The device commands install the project's app and instrumentation APKs on exactly
one authorized USB phone. They start a hidden Windows dry-run host, temporarily
map required ports, verify traces and restore previous ADB mappings. Video tests
briefly display a project-owned Direct3D pattern window and never call SendInput.

Device used: model 22021211RC, Android 14 / API 34, physical display 1080 x 2400.
The tests inject synthetic native MotionEvents, including seven pointers with
reordered indices, on the device. They do not prove physical digitizer behavior.
The actual USB/TCP test produced DOWN 1..6, ABS 640 360 and UP 1..6 in the Windows
trace. It exercises the real socket transport but uses a dry-run input sink.

Local artifacts and evidence (ignored by Git):

- `dist/InFalsusTouchHost.exe` — Release, static C++ runtime.
- `dist/InFalsusTouch.apk` — debug signed Android controller/video prototype.
- `android/*/build/test-results/test/` — JUnit XML.
- `android/app/build/reports/lint-results-debug.html` — lint report.
- `android/transport/build/interop/` — native-host integration logs and traces.
- `build/device-test/instrumentation.txt` and `input-trace.txt` — physical USB run.
- `build/device-test/phase1-screen.png` — actual device screenshot, inspected.
- `build/video-test/metrics.json`, `host.log`, `pattern.log`, `sample.h264` — PC hardware stream test.
- `build/video-device-test/instrumentation.txt`, `input-trace.txt`, `video-metrics.json` — real USB video test.
- `build/video-device-test/video-surface.png` — decoded Surface pixels, six color bands checked.
- `build/video-device-test/video-screen.png` — actual device UI with video and lane overlay, inspected.

## Video measurements

Test hardware: NVIDIA GeForce RTX 4050 Laptop GPU, NVIDIA async/D3D11 H.264 MFT,
165 Hz PC display, Android 14 model 22021211RC. The source is a moving Direct3D
color pattern. These short runs are not measurements of In Falsus under load.

- PC run: 482 frames in 8.013 seconds, 60.15 FPS, 17 IDRs; SPS profile is Baseline.
- PC frame-available to encoded-output mean: 3.31 ms. This excludes game rendering
  and time before WGC handed the frame to the application.
- Concurrent local dry-run input RTT: median 0.158 ms, maximum 0.289 ms.
  This local loopback test does not include USB; the phone reports its own RTT.
- Phone steady interval: 7.017 seconds, receive 60.00 FPS, presentation callbacks
  59.00 FPS while Field and six lanes remained held. Queue depth at the final
  sample was zero. Thirty compressed/decoded frames were dropped over the session,
  including startup recovery; this is not a zero-drop claim.
- Last phone sample: decoder 5.32 ms, complete-packet receive to presentation
  callback 35.63 ms. These are individual local stage samples, not percentiles or
  glass-to-glass latency. Earlier samples varied; no cross-device clocks are subtracted.

## Resolved issues

- Java Selector initialization failed in the inherited packaged Windows temp
  directory. A minimal TCP/Pipe/Selector probe isolated the Unix-domain socket
  path; using project-local `jdk.net.unixdomain.tmpdir` passed all probes/builds.
- Pinned Android command-line tools 19.0 and a package-list file avoid the newer
  compatibility wrapper splitting SDK package names. Archives are checksum checked.
- An integration test caught rounding of the 1280-pixel Field center to 639.
  Weighted endpoint interpolation now rounds it to 640; a regression test covers it.
- Native-host test traces are retained under the build directory, avoiding a
  Windows file-lock race during immediate test-file cleanup and retaining evidence.
- WGC's default minimum interval was 160000 (100 ns units), yielding 55 FPS on a
  165 Hz source. Uncapping supported WGC sessions and pacing before encoding
  restored approximately 60 FPS; the source continued updating at about 165 FPS.
- A video timestamp check exposed unsigned subtraction when a platform frame
  timestamp exceeded the sampled output time. Capture statistics now
  start at application frame availability on the same QPC clock as encode/send.
- The phone's ActivityScenario launch stalled during the first video attempt.
  The device harness now launches the exact MAIN/LAUNCHER intent through the
  instrumentation shell, observes the resumed Activity and uses bounded waits.
- Codec startup can outrun the tiny receive queue. Recovery waits for an IDR,
  flushes and resubmits SPS/PPS instead of decoding a broken dependency chain.

## Remaining acceptance

Real SendInput acceptance by In Falsus, physical seven-finger capacity, actual
cable-unplug detection time, multi-monitor/DPI behavior and user-perceived input
latency still need gameplay testing. Long thermal/stability runs, 1080p60,
multi-monitor capture, window resize/device-loss recovery and physical
glass-to-glass latency remain unverified. Full settings persistence, calibration
workflow and exposing Overlay/Reserved/Fit/Fill/Crop are the next UX work.
See `tests/manual-acceptance.md` for the remaining physical checks.
