# Implementation status

## Current milestone

Phase 1 input, Phase 2 hardware video and Phase 4 settings prototypes are implemented and verified
on 2026-09-28. The connected Android 14 phone passed real USB H.264 decoding,
rendered color checks, seven synthetic pointers during playback and reconnect.
Bounded queues, frame pacing and local latency statistics provide the Phase 3
baseline. Settings now persist on both devices, with phone/PC Field calibration,
Aligned/Overlay/Reserved layouts, Fit/Stretch/Crop, USB discovery and PC video-quality profiles.
The latest aligned layout has taller 40% buttons, native pressed feedback, six-point
judgment calibration, centered full-frame video and an independent 120 Hz display hint.
Physical gameplay and absolute Field alignment remain open; the full goal is active.

## Acceptance gates

| Gate | Status |
| --- | --- |
| Architecture and input protocol | Defined |
| Windows host compilation | Passed, MSVC 19.44 / CMake 3.31.6 / Windows SDK 10.0.26100.0 |
| Android APK compilation | Passed, Gradle 8.11.1 / AGP 8.9.2 / Kotlin 2.1.20 / JDK 21 |
| C++ input protocol / input state / mapping / video / profile suites | 5/5 passed |
| Kotlin settings / touch / input+video protocol / queue / socket / native-host tests | 30/30 passed, no skips |
| Native profile persistence and CLI precedence integration | Passed, including invalid/missing profile and unchanged-file failure checks |
| TCP disconnect / malformed / reconnect integration | 8/8 checks passed |
| Android lint | Passed, no issues found |
| Android 14 device MotionEvent / USB / settings / persistence / calibration instrumentation | 9/9 passed |
| Android Activity launch and landscape screen inspection | Passed at 2400 x 1080 |
| Physical finger tracking and full In Falsus chart gameplay | Not tested |
| Actual In Falsus USB SendInput | Shift+Space starts the 1.0.4b tutorial; full chart input remains open |
| Aligned game video / enlarged seven-pointer transport / feedback screenshots | 1/1 passed with real game capture and dry-run input |
| OS native scan codes, Field mapping, EOF/watchdog/focus release | Passed against project-owned Win32 target; game cursor mapping is separate |
| Phone display and centered Fit | Panel/app 120 Hz; complete 1920 x 1080 frame at (240, 0) on 2400 x 1080 |
| WGC / GPU conversion / hardware H.264 / TCP | Passed, including Baseline SPS and >=58 FPS gate |
| Android hardware decode / output colors / seven-pointer input / settings and calibration isolation / reconnect | 1/1 device integration test passed |
| 720p60 short-run throughput | PC 60.15 FPS; latest USB phone steady receive 59.99 FPS, present callbacks 58.85 FPS |
| Measured latency tuning | Bounded queues, WGC pacing, low-latency codec selection and local statistics implemented |
| Settings / calibration UX | Implemented; phone persistence/dialog/calibration and PC profile/mapping checks passed; physical PC cursor wizard use remains manual |

## Reproduce

```powershell
.\scripts\build-windows.ps1
.\scripts\build-android.ps1 -DeviceTests
uv run --python 3.13 tests\tcp-integration.py --host dist\InFalsusTouchHost.exe
uv run --python 3.13 tests\profile-integration.py
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
- `build/device-test/settings-screen.png` — settings dialog screenshot.
- `build/video-test/metrics.json`, `host.log`, `pattern.log`, `sample.h264` — PC hardware stream test.
- `build/video-device-test/instrumentation.txt`, `input-trace.txt`, `video-metrics.json` — real USB video test.
- `build/video-device-test/video-surface.png` — decoded Surface pixels, six color bands checked.
- `build/video-device-test/video-screen.png` — actual device UI with video and lane overlay, inspected.
- `build/video-device-test/calibration-screen.png` — full-screen Field calibration with video still playing, inspected.

## Video measurements

Test hardware: NVIDIA GeForce RTX 4050 Laptop GPU, NVIDIA async/D3D11 H.264 MFT,
165 Hz PC display, Android 14 model 22021211RC. The source is a moving Direct3D
color pattern. These short runs are not measurements of In Falsus under load.

- PC run: 482 frames in 8.013 seconds, 60.15 FPS, 17 IDRs; SPS profile is Baseline.
- PC frame-available to encoded-output mean: 3.31 ms. This excludes game rendering
  and time before WGC handed the frame to the application.
- Concurrent local dry-run input RTT: median 0.158 ms, maximum 0.289 ms.
  This local loopback test does not include USB; the phone reports its own RTT.
- Latest phone steady interval after the settings/layout change: 7.017 seconds,
  receive 59.99 FPS, presentation callbacks 58.85 FPS while Field and six lanes
  remained held. Queue depth at the final sample was zero. Twenty-nine
  compressed/decoded frames were dropped over the session,
  including startup recovery; this is not a zero-drop claim.
- Last phone sample: decoder 7.33 ms, complete-packet receive to presentation
  callback 37.66 ms. These are individual local stage samples, not percentiles or
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
- Dialog dismissal can run after the calibration overlay is created. Input
  gating now checks the active overlay before restoring control. The USB video
  test opens settings while six keys are held, enters calibration, injects fresh
  synthetic touches, and verifies exactly one down/up pair per lane at the Host.

## Remaining acceptance

Complete six-key chart acceptance by In Falsus, physical seven-finger capacity, actual
cable-unplug detection time, multi-monitor/DPI behavior and user-perceived input
latency still need gameplay testing. Long thermal/stability runs, 1080p60,
multi-monitor capture, window resize/device-loss recovery and physical
glass-to-glass latency remain unverified. Actual 1.0.4b tutorial probes show that
the game locks/recenters the OS cursor. Default absolute mapping moves the game's
Field cursor over only part of its range; visual alignment of the phone's judgment
lines does not establish touch-to-game-cursor alignment. Relative probes move the
cursor, but a verified absolute mapping solution is still required.
See `tests/manual-acceptance.md` for the remaining physical checks.

## Real-game and native input evidence

- Official Steam instructions and the installed tutorial distinguish keyboard
  lower notes from mouse upper Field. Central lanes are ASDF; outer lanes are
  Shift/Space with slanted judgment lines. The preset follows this observed 16:9 layout.
- Latest real-game video interval: 10.021 s, receive 59.98 FPS, present callbacks
  57.28 FPS, queue depth 0, 29 session drops including startup. Last local samples:
  PC available-to-encode 3.08 ms, decoder 6.44 ms, receive-to-present 35.16 ms.
- Panel/app mode was 120 Hz, requested 120 Hz, Choreographer callbacks 119.65 Hz.
  Earlier system-selected mode was also 120 Hz; the preference is not claimed to
  improve this device's already-selected rate. Source video remains 60 FPS.
- `build/game-video-test/` contains metrics, full-frame and pressed screenshots,
  and exactly one DOWN/UP pair for each of six enlarged keys plus Field transport.
  Windows input is dry-run for this capture/feedback test.
- `build/game-input-test/lane-1-6.txt`: phone USB Shift+Space passed; visual inspection
  confirmed the actual game entered its tutorial. No full-song performance is claimed.
- `build/native-input-test/metrics-focus.json`: physical client coordinates match
  at left/center/right; focus-loss release observed in 34.13 ms by the test driver.
  Separate silent-connection watchdog observation was 507.37 ms. These are samples,
  not hard OS scheduling bounds or game latency measurements.
- The native key receiver observed Shift/A/S/D/F/Space scans 42/30/31/32/33/57.
  An IME initially changed key processing; testing with plain US layout resolved it.
  Host now prints the target keyboard layout and warns for CJK IME layouts.
