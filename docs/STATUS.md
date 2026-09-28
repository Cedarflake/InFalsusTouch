# Implementation status

## Current milestone

Phase 1 — input prototype implemented and build/simulation gates passed on
2026-09-28. A connected Android 14 phone also passed native MotionEvent and real
USB transport tests. Physical multi-finger gameplay and In Falsus input acceptance
remain unverified. The full product goal remains active; Phase 2 is next.

## Acceptance gates

| Gate | Status |
| --- | --- |
| Architecture and input protocol | Defined |
| Windows host compilation | Passed, MSVC 19.44 / CMake 3.31.6 / Windows SDK 10.0.26100.0 |
| Android APK compilation | Passed, Gradle 8.11.1 / AGP 8.9.2 / Kotlin 2.1.20 / JDK 21 |
| C++ protocol / input state / mapping suites | 3/3 passed |
| Kotlin touch / protocol / queue / socket / native-host tests | 18/18 passed, no skips |
| TCP disconnect / malformed / reconnect integration | 8/8 checks passed |
| Android lint | Passed, no issues found |
| Android 14 device MotionEvent + USB transport instrumentation | 4/4 passed |
| Android Activity launch and landscape screen inspection | Passed at 2400 x 1080 |
| Physical finger tracking and In Falsus gameplay | Not tested |
| Video capture / encode / decode | Phase 2, not implemented |
| Measured latency tuning | Phase 3, not implemented |
| Full settings / calibration UX | Phase 4, not implemented |

## Reproduce

```powershell
.\scripts\build-windows.ps1
.\scripts\build-android.ps1 -DeviceTests
uv run --python 3.13 tests\tcp-integration.py --host dist\InFalsusTouchHost.exe
.\scripts\test-device.ps1 -SkipBuild
```

The final command installs the project's app and instrumentation APKs on exactly
one authorized USB phone. It starts a hidden Windows dry-run host, temporarily
maps the control port, verifies the trace, then restores previous ADB mappings.

Device used: model 22021211RC, Android 14 / API 34, physical display 1080 x 2400.
The tests inject synthetic native MotionEvents, including seven pointers with
reordered indices, on the device. They do not prove physical digitizer behavior.
The actual USB/TCP test produced DOWN 1..6, ABS 640 360 and UP 1..6 in the Windows
trace. It exercises the real socket transport but uses a dry-run input sink.

Local artifacts and evidence (ignored by Git):

- `dist/InFalsusTouchHost.exe` — Release, static C++ runtime.
- `dist/InFalsusTouch.apk` — debug signed Android input prototype.
- `android/*/build/test-results/test/` — JUnit XML.
- `android/app/build/reports/lint-results-debug.html` — lint report.
- `android/transport/build/interop/` — native-host integration logs and traces.
- `build/device-test/instrumentation.txt` and `input-trace.txt` — physical USB run.
- `build/device-test/phase1-screen.png` — actual device screenshot, inspected.

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

## Remaining acceptance

Real SendInput acceptance by In Falsus, physical seven-finger capacity, actual
cable-unplug detection time, multi-monitor/DPI behavior and user-perceived input
latency still need gameplay testing. No video or glass-to-glass latency result is
claimed. See `tests/manual-acceptance.md` for the remaining physical checks.
