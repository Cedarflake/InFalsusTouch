# Implementation status

## Current milestone

The 0.5 prototype adds Flutter Material 3 English/Chinese settings, per-phone control
selection, seven-controller cooperative input, shared video encoding and read-only
IF key-binding synchronization. Phase 1 input, Phase 2 hardware video and Phase 4 settings
prototypes are implemented and verified on 2026-09-28. The connected Android 14 phone passed real USB H.264 decoding,
rendered color checks, seven synthetic pointers during playback and reconnect.
Bounded queues, frame pacing and local latency statistics provide the Phase 3
baseline. Settings now persist on both devices, with phone/PC Field calibration,
Aligned/Overlay/Reserved layouts, Fit/Stretch/Crop, USB discovery and PC video-quality profiles.
The latest aligned layout has taller 40% buttons, native pressed feedback, six-point
judgment calibration, centered full-frame video and an independent 120 Hz display hint.
Physical gameplay acceptance remains open. Relative Field is now the default,
following the user's tablet trial and game-managed sensitivity decision.
The replacement absolute path now passes game-state positioning checks on the
supported 1.0.4b build; physical phone/video alignment remains open.
The 2026-09-29 UI update consolidates USB logs, centers partial key selections,
adds automatic settings saving and appearance preferences, and keeps the connection
action on the status row. The Controls page combines cooperative play and key/IME
guidance in one top card. Immediate action and validation messages now use localized
Android system Toasts, including calibration without video and failed settings saves.
The full 12-test device suite passed, followed by two focused Toast/entry tests after
checking that page changes do not prematurely cancel error feedback.

The latest Field update preserves actual horizontal touch pixels in Relative mode,
including fast swipes and movement outside calibrated edges. Host adds no gain,
acceleration, smoothing or speed limiting. Direct Absolute reads the game's raw
position and effective sensitivity, then sends relative corrections without
duplicating pending input. Repeated taps and 240 targets at 120 events/second passed
in a normal chart. No fixed sensitivity is required.
Old phone preferences switch to Relative while preserving other settings; old
Host profiles retain calibration/video and retire their relative tuning values.
[FIELD-MAPPING.md](FIELD-MAPPING.md) records the game probes and comparisons with
InFalsusTouchTool, Moonlight and Sunshine.

Touch settings now include optional Button vibration, off by default, with English/
Chinese labels and automatic persistence. Native virtual-key feedback runs locally
on a lane's first DOWN, independently of Host acknowledgement. Long holds, UP and
Field sliding do not retrigger it. The system haptic preference is respected.
Field's visible touch boundary now follows the selected buttons' upper edges,
including slanted side keys. This removes the approximately 41-pixel dead strip
reported with 30% button height and the user's calibration; judgment positions
and button shapes are unchanged.

The 2026-09-29 connection update makes Host maintain forwarding for authorized USB
phones and packages ADB beside the executable. Lost forwarding, Host restart and
ADB server restart passed phone ACK/input checks. This machine also had spontaneous
USB read failures with ADB 37.0.1's new Windows backend; its native compatibility
backend is now used when Host starts an ADB server. A short observation without
further read failures is not long-term reliability acceptance.
The exact ground-ground-Field pointer order now passes the native regression test,
but physical three-finger input still receives Android cancellation while hardware
touch points remain active. System gesture settings were not changed. Evidence and
remaining limits are in [USB-RECOVERY.md](USB-RECOVERY.md).

## Acceptance gates

| Gate | Status |
| --- | --- |
| Architecture and input protocol | Defined |
| Windows host compilation | Passed, MSVC 19.44 / CMake 3.31.6 / Windows SDK 10.0.26100.0 |
| Android APK compilation | Passed, Gradle 8.11.1 / AGP 8.9.2 / Kotlin 2.1.20 / JDK 21 |
| C++ input protocol / input state / mapping / video / profile / cooperative / USB / window suites | 8/8 passed |
| Kotlin settings / touch / input+video protocol / queue / socket / native-host / video timing tests | 51/51 passed, no skips; includes gap-free Field/button boundaries, pixel-preserving relative movement, large swipes, native-host deltas, haptic-setting codecs, 186 partial-selection/layout combinations, bounded local-clock latency statistics, high-frame-rate protocol bounds, callback accounting after timing eviction and codec-buffer ownership across flush/error/close |
| Flutter analysis and UI tests | No analysis issues; 20/20 tests passed, including bilingual vibration toggle and immediate saving |
| Native profile persistence and CLI precedence integration | Passed, including legacy tuning migration, retired flags, invalid/missing profile and unchanged-file failure checks |
| TCP disconnect / malformed / reconnect integration | 8/8 checks passed, including coalesced full-width relative movement |
| Android lint | Latest profile report passed, 0 errors and 2 advisory KTX suggestions |
| Cooperative TCP input and live key sync | Seven simulated clients, seven scenarios passed; physical multi-phone run pending |
| Shared hardware video broadcast | Six healthy simulated viewers plus one stalled viewer passed; one shared encoding verified |
| Android 14 device MotionEvent / USB / settings persistence and migration | Latest 8/8 input checks passed, including final-up sample, 2400-pixel swipes and vibration preference persistence; earlier 12/12 UI suite and 2/2 focused Toast/entry checks passed |
| Android Activity launch and landscape screen inspection | Passed at 2400 x 1080 |
| Physical finger tracking and full In Falsus chart gameplay | User reports improved hand feel after the input update; systematic full-chart and alignment acceptance remains open |
| Actual In Falsus USB SendInput | Shift+Space starts the 1.0.4b tutorial; full chart input remains open |
| Actual-chart absolute Field mapping | Replacement passed repeated targets and 120-event/s sweep with read-only game feedback; largest settled normalized error 0.000317; physical screen alignment remains open. Earlier OS-absolute failure remains documented in [FIELD-MAPPING.md](FIELD-MAPPING.md) |
| Actual-chart relative Field spot check | Small right/left reversal returned to center; one displacement and five increments reached similar positions; physical swipes and hand feel remain open |
| Aligned game video / enlarged seven-pointer transport / feedback screenshots | 1/1 passed with real game capture and dry-run input |
| OS native scan codes, Field mapping, EOF/watchdog/focus release | Passed against project-owned Win32 target; game cursor mapping is separate |
| Phone display and centered Fit | Panel/app mode 120 Hz; complete image displayed in a 1920 x 1080 rectangle at (240, 0) on 2400 x 1080 |
| WGC / GPU conversion / hardware H.264 / TCP | Passed, including Baseline SPS and >=58 FPS gate |
| PC source resize / aspect changes / minimize and restore | 720p decoded color, centered bars, animation and independent input passed; minimize triggered a stream restart which the test receiver recovered |
| Android hardware decode / output colors / seven-pointer input / settings and calibration isolation / reconnect | Passed at both 720p and 1080p with relative Field movement and restored device settings |
| Android streaming lifecycle and input cleanup | 9/9 transitions passed at 720p60: three Activity recreations, three background/resume cycles and three Surface recreations; 10 six-key holds/releases, no stale gesture replay or duplicate I/O workers |
| 720p60 short-run throughput | Latest callback-driven USB phone steady receive 60.00 FPS, decode 59.91 FPS, present callbacks 59.46 FPS; 11.05-second steady interval; no unmatched presentation callbacks |
| Experimental 720p120 throughput | PC 120.13 FPS passed; latest callback-driven phone receive 119.98 FPS, decode 116.49 FPS, present callbacks 86.89 FPS still failed the unchanged proportional presentation gate; default remains 60 FPS |
| 1080p60 short-run throughput | Earlier PC 60.13 FPS; latest callback-driven USB phone steady receive 60.01 FPS, decode 59.97 FPS, present callbacks 59.35 FPS; native source and received dimensions verified |
| Measured latency tuning | Bounded queues, WGC pacing, low-latency codec selection and local statistics implemented |
| Settings / calibration UX | Implemented; phone persistence/dialog/calibration and PC profile/mapping checks passed; physical PC cursor wizard use remains manual |

## Reproduce

```powershell
.\scripts\build-windows.ps1
.\scripts\build-android.ps1 -DeviceTests
uv run --python 3.13 tests\tcp-integration.py --host dist\InFalsusTouchHost.exe
uv run --python 3.13 tests\multiplayer-integration.py
uv run --python 3.13 tests\profile-integration.py
.\scripts\test-device.ps1 -SkipBuild
.\scripts\test-device.ps1 -SkipBuild -InputOnly
uv run --python 3.13 tests\video-integration.py --seconds 8 --min-fps 58
uv run --python 3.13 tests\video-integration.py --resolution 1080p --seconds 10 --min-fps 58
uv run --python 3.13 tests\video-recovery.py
uv run --python 3.13 tests\video-device.py
uv run --python 3.13 tests\video-device.py --resolution 1080p --skip-install
# Build matching profile application/instrumentation first for lifecycle acceptance.
.\scripts\build-android.ps1 -Mode profile -DeviceTests
uv run --python 3.13 tests\video-device.py --build-mode profile --lifecycle-cycles 3
uv run --python 3.13 tests\video-integration.py --fps 120 --seconds 10 --min-fps 116
uv run --python 3.13 tests\video-device.py --fps 120 --seconds 20 --skip-install
uv run --python 3.13 tests\video-composition.py
uv run --python 3.13 tests\video-composition.py --probe surface-hints --fps 60
uv run --python 3.13 tests\video-composition.py --probe surface-hints --fps 120 --skip-install
uv run --python 3.13 tests\multiplayer-video.py
```

The device commands install the project's app and instrumentation APKs on exactly
one authorized USB phone. They start a hidden Windows dry-run host, temporarily
map required ports, verify traces and restore previous ADB mappings. Video tests
briefly display a project-owned Direct3D pattern window and never call SendInput.

Device used: model 22021211RC, Android 14 / API 34, physical display 1080 x 2400.
The tests inject synthetic native MotionEvents, including seven pointers with
reordered indices, on the device. They do not prove physical digitizer behavior.
The input suite's USB/TCP test produced DOWN 1..6, ABS 640 360, REL 1280,
REL -320 and UP 1..6. It also verifies a 2400-pixel swipe as REL 1280 + REL 1120
and its full reversal. The latest 720p/1080p video probes also passed with actual
View-pixel displacement: +600/-300 on the 2400-pixel controller. The first rerun
incorrectly used the resource display width (2320 pixels after cutout accounting)
for its expected value; instrumentation now records the actual touched View width.
The failed run is retained at `build/video-device-test/720p-20260928T204340679677Z/`.
These socket tests use a dry-run input sink.

Local artifacts and evidence (ignored by Git):

- `dist/InFalsusTouchHost.exe` — Release, static C++ runtime.
- `dist/InFalsusTouch.apk` — debug signed Android controller/video prototype.
- `android/*/build/test-results/test/` — JUnit XML.
- `android/app/build/reports/lint-results-debug.html` — lint report.
- `android/transport/build/interop/` — native-host integration logs and traces.
- `build/device-test/instrumentation.txt` and `input-trace.txt` — physical USB run.
- `build/device-test/toast-instrumentation.txt` — final bilingual Toast and double-tap entry checks.
- `build/device-test/phase1-screen.png` — actual device screenshot, inspected.
- `build/device-test/flutter-settings-*.png` — settings screenshots from the prior UI build.
- `build/video-test/metrics.json`, `host.log`, `pattern.log`, `sample.h264` — PC hardware stream test.
- `build/video-device-test/instrumentation.txt`, `input-trace.txt`, `video-metrics.json` — real USB video test.
- `build/video-device-test/video-surface.png` — decoded Surface pixels, six color bands checked.
- `build/video-device-test/video-screen.png` — actual device UI with video and lane overlay, inspected.
- `build/video-device-test/calibration-screen.png` — full-screen Field calibration with video still playing, inspected.

## Video measurements

### Late-judgment investigation, 2026-09-29

The user reports frequent Late judgments while playing muted and reading the phone
display; normal audio output is the PC. Saved IF audio offset was read as zero and
was not changed. Read-only analysis of the exact 1.0.4b binary now traces the saved
offset into the gameplay clock used by input processing and track rendering.
Increasing it makes that clock earlier at a fixed player position, so a positive
adjustment is the direction to test for Late presses relative to music. It also
shifts the displayed chart; it is not independent judgment compensation for muted
phone-screen play. [TIMING-CALIBRATION.md](TIMING-CALIBRATION.md) records the evidence,
scope and comparison procedure. No physical compensation value or improvement in
the user's judgments has been established.

The phone's Qualcomm OMX AVC decoder does not advertise Android's standard
low-latency capability or the vendor option through parameter discovery. The
known Qualcomm low-latency extension was accepted when requested explicitly.
Surface rendering now uses Android-local time rather than treating a PC frame
identifier as a display deadline. These changes are compatible with both tested
resolutions, but the measured difference does **not** establish a latency reduction.

The following means and P95s cover the last 240 presented frames of each pattern
run, not the entire session. Each steady throughput interval lasted approximately
21 seconds; the display stayed at 120 Hz and source video at 60 FPS.

| Configuration | Receive to submit mean | Decode mean | Decode to present mean | Receive to present mean / P95 |
| --- | --- | --- | --- | --- |
| 720p baseline | 1.49 ms | 5.35 ms | 23.80 ms | 30.64 / 37.19 ms |
| 720p local presentation clock; no vendor option | 2.10 ms | 5.83 ms | 23.99 ms | 31.93 / 38.65 ms |
| 720p Qualcomm extension | 1.54 ms | 4.93 ms | 23.95 ms | 30.42 / 36.33 ms |
| 1080p Qualcomm extension | 2.03 ms | 6.74 ms | 24.20 ms | 32.97 / 38.19 ms |

The largest measured phone stage remains presentation after decoded output.
Read-only SurfaceFlinger samples separately showed an 8.33 ms display period and
roughly 14–23 ms from a ready buffer to presentation. They are separate observations,
not a synchronized trace of the same frames. End-of-run USB input RTT samples were
6.50, 3.19, 3.53 and 2.84 ms respectively; these are round trips for software-created
input, not physical finger-to-game latency or a one-way estimate.

All four complete runs passed decoded colors, six held keys plus moving Field,
settings/calibration isolation, balanced releases and reconnection. User preferences
were compared before/after and restored exactly, as were the ADB mappings.
The evidence directories under `build/video-device-test/` are respectively:
`720p-20260928T205139413436Z/`, `720p-20260928T205619379661Z/`,
`720p-20260928T210026078297Z/` and `1080p-20260928T210351331063Z/`.

A subsequent capture of IF's current song-selection screen passed centered Fit,
seven-pointer transport and local feedback checks. Over 10.00 seconds it received
60.07 FPS and reported 59.37 presentation callbacks/s. The final 240-frame window
averaged 26.74 ms receive-to-present (P95 31.08 ms), including 0.85 ms waiting to
submit, 2.86 ms decoding and 23.03 ms afterward. Input RTT's last sample was 4.69 ms.
The inspected screenshot and metrics are retained in
`build/game-video-latency-20260928T210530Z/`. Windows input stayed in dry-run mode;
the song-selection scene does not establish busy-chart or physical timing behavior.

The remaining diagnosis requires comparing the same phone controls while watching
the PC versus the phone, plus real chart timing results. These tests do not prove
that the reported Late judgments are resolved. No total touch-to-photon latency
is inferred from independent PC and phone clocks.

### High-frame-rate probe, 2026-09-29

Host CLI/profile validation and both IFV1 implementations now accept 24–120 FPS.
Defaults remain 720p60. The APK reports the requested rate separately from actual
receive, decode and presentation counts. Test runners accept an explicit Host
binary and FPS so experiments do not replace the running Host. Failed throughput
checks retain their fresh measurements rather than losing the diagnostic data.

The PC-only 720p120 run delivered 120.13 FPS for 10.01 seconds; capture-available to
encode averaged 2.71 ms. Evidence: `build/video-test/720p120-20260928T212040949869Z/`.
Two USB phone runs did not pass the presentation gate of at least 110 FPS. The
first reported 86.70 callbacks/s. The second added cumulative decode counts to
locate the deficit, without changing playback or lowering the gate:

| Requested stream | Steady interval | Receive FPS | Decode FPS | Present callbacks/s | Receive to present mean / P95 |
| --- | --- | --- | --- | --- | --- |
| 720p120 | 28.03 s | 120.03 | 113.68 | 87.66 | 29.71 / 35.84 ms |
| 720p60 regression, same build | 21.02 s | 59.99 | 59.95 | 59.33 | 31.81 / 37.68 ms |

Both used the 120 Hz display and Qualcomm low-latency option. Latency windows still
cover only the last 240 presented frames, approximately 2.7 and 4.0 seconds, not the
whole steady interval. At 120 FPS, mean receive-to-submit/decode/decode-to-present
were 2.12/5.00/22.60 ms; at 60 FPS they were 2.10/5.52/24.19 ms. The small difference
does not establish a useful latency reduction. Presentation callbacks are software
measurements, not a high-speed recording of the physical screen.

PC encoding and phone reception sustained the requested 120 FPS, while the larger
deficit occurred after decoded output. Decoder/queue behavior and presentation
cadence need further isolation; these results do not justify blaming USB speed or
claiming that the phone cannot decode high-frame-rate video. The 60 FPS regression
passed colors, simultaneous input, settings/calibration isolation and reconnect.
All runs restored phone preferences exactly and restored the previous ADB mappings.

Evidence under `build/video-device-test/`: `720p120-20260928T212411027376Z/`
(first failure), `720p120-20260928T212919147260Z/` (full stage metrics), and
`720p60-20260928T213154961149Z/` (passing regression). High-rate operation remains
experimental; it is not enabled in the user's normal connection. No new real-game
120 FPS or physical Late-judgment acceptance is claimed.

### Overlay isolation and callback accounting, 2026-09-29

`tests/video-composition.py` compares normal controls, Flutter hidden, all control
overlays hidden, and normal controls restored within one continuous 720p60 stream.
Each phase lasts approximately eight seconds, uses the same decoder and 120 Hz
display, and samples its last 240 matched presentation callbacks. The test checks
view visibility and captures screenshots; the inspected normal/video-only images
confirm the intended layers were hidden. No input is injected into Windows.

| Phase | Present callbacks/s | Decode mean | Decode to present mean | Receive to present mean / P95 |
| --- | --- | --- | --- | --- |
| Normal, before | 59.96 | 2.67 ms | 22.82 ms | 26.29 / 29.79 ms |
| Native controls only | 59.67 | 5.13 ms | 23.68 ms | 30.57 / 35.71 ms |
| Video only | 59.55 | 5.26 ms | 23.62 ms | 30.67 / 35.75 ms |
| Normal, restored | 59.66 | 5.43 ms | 24.24 ms | 31.51 / 38.20 ms |

Hiding the visible overlays did not remove the roughly 23–24 ms post-decode stage.
The first normal phase also decoded faster than the final normal phase, so this
single ordered probe cannot establish a causal cost for Flutter visibility or
rule out runtime/power-state effects. It provides no basis for removing the
requested Flutter UI. Phone preferences and USB mappings were restored exactly.
Evidence: `build/video-composition-test/20260928T214640915271Z/`.

Source inspection identified a separate measurement defect: once 64 unmatched
render records accumulated, the old code cleared the entire map, including
recent frames still awaiting callbacks. It also counted a rendered callback only
when a timing record existed. The replacement retires only the oldest record,
counts every callback and reports unmatched callbacks separately. Latency still
uses only matched records. Unit tests cover overflow, out-of-order callbacks,
flush and counting a callback whose timing has expired. This is a measurement
correction, not a change to physical rendering speed. Earlier values above are
retained as measurements from the earlier implementation.

The corrected implementation was rebuilt and installed. The 120 FPS retest still
failed its >=110 presentation-callbacks/s gate, while the 60 FPS regression passed
colors, seven-pointer input, settings/calibration isolation and reconnect:

| Stream after correction | Steady interval | Receive FPS | Decode FPS | Present callbacks/s | Receive to present mean / P95 |
| --- | --- | --- | --- | --- | --- |
| 720p120 | 26.04 s | 120.00 | 118.88 | 93.27 | 28.53 / 35.00 ms |
| 720p60 | 21.02 s | 59.99 | 59.99 | 59.57 | 31.94 / 37.77 ms |

Both runs reported zero unmatched presentation callbacks. At 120 FPS the final
240 matched frames averaged 1.67 ms receive-to-submit, 4.52 ms decode and 22.35 ms
decode-to-present. At 60 FPS these were 2.20, 5.58 and 24.16 ms. The missing
presentation rate is therefore not solely an artifact of discarded timing
records. These new runs are not evidence that the accounting fix accelerated
physical video, and neither establishes that Late judgments are resolved.
Evidence: `build/video-device-test/720p120-20260928T215323572836Z/` and
`build/video-device-test/720p60-20260928T215607231097Z/`. User preferences and ADB
mappings were restored exactly; the updated APK resumed the normal 720p60 Host.

### Surface frame-rate hints and compositor timestamps, 2026-09-29

The presentation probe now also compares `FIXED_SOURCE`, `DEFAULT` compatibility,
and clearing the Surface frame-rate hint. Fixed-source control phases bracket
the alternatives. All gameplay/UI layers remain visible, and each phase settles
for at least five seconds before an eight-second measurement. The settling period
excludes immediate transitions and recovery after the previous screenshot/dump;
it does not establish equivalent runtime or power conditions between phases.

The rebuilt APK passed all 48 Kotlin tests and Android lint. On the same 120 Hz
phone, the 60 FPS presentation gate passed and the 120 FPS gate still failed:

| Stream | Phase | Receive / decode FPS | Present callbacks/s | Software drops | Receive to callback timestamp mean / P95 |
| --- | --- | --- | --- | --- | --- |
| 60 | Fixed, before | 60.08 / 59.95 | 59.45 | 0 | 29.17 / 35.81 ms |
| 60 | Default compatibility | 60.01 / 60.13 | 59.26 | 0 | 31.43 / 37.37 ms |
| 60 | Fixed, middle | 60.05 / 60.05 | 59.18 | 0 | 31.15 / 36.87 ms |
| 60 | No hint | 60.05 / 59.92 | 58.80 | 0 | 30.73 / 36.72 ms |
| 60 | Fixed, after | 59.94 / 60.19 | 59.19 | 0 | 32.50 / 43.12 ms |
| 120 | Fixed, before | 119.98 / 119.86 | 98.38 | 0 | 27.56 / 34.79 ms |
| 120 | Default compatibility | 119.94 / 98.49 | 73.81 | 171 | 30.68 / 36.35 ms |
| 120 | Fixed, middle | 120.00 / 59.87 | 43.28 | 483 | 30.92 / 38.12 ms |
| 120 | No hint | 120.02 / 110.16 | 81.72 | 79 | 30.62 / 37.48 ms |
| 120 | Fixed, after | 119.97 / 97.00 | 70.78 | 184 | 30.17 / 35.42 ms |

Rates and drop counts cover each measurement interval. Latency summarizes only
the final 240 matched callbacks, excluding dropped frames. All phases had zero
unmatched callbacks. The fixed-source controls also changed over time, so the
ordered run does not isolate a causal effect of a particular hint. There is no
evidence here to change the production hint or enable 120 FPS by default.

The decoder now retains the exact local timestamp passed to `releaseOutputBuffer`.
At each phase end, the test saves SurfaceFlinger's raw frame history and matches
that timestamp exactly to its desired-present column. All ten sampled frames
matched uniquely. Their codec callback timestamps were 5.35–5.72 ms later than
the corresponding compositor actual-present timestamps. These are ten individual
frames, not latency percentiles, physical scanout measurements or a demonstrated
speedup. No constant is subtracted from production statistics. The compositor's
ready column equaled the requested release timestamp in these samples, so it
does not independently measure hardware decode completion.

The 120 FPS compositor histories also contained only 23.47–94.16 unique presents/s
over their final 1.34–5.37-second tails. Those tails differ from the eight-second
callback intervals and must not be compared as if they cover identical frames.
They nevertheless provide separate evidence of irregular high-rate presentation.
Software queue recovery and decoder scheduling remain investigation targets;
120 Hz panel support is not evidence that the full stream sustains 120 FPS.

Column meanings follow [AOSP FrameTracker](https://android.googlesource.com/platform/frameworks/native/+/cdb6b16dec3a541b455be99d075004cb2f0a0cd7/services/surfaceflinger/FrameTracker.cpp).
The [codec callback API](https://developer.android.com/reference/android/media/MediaCodec.OnFrameRenderedListener)
distinguishes the reported render timestamp from delayed/batched callback delivery.
The probe uses that reported timestamp, not the time its handler executes.

Evidence: `build/video-surface-hints-test/60fps-20260928T224653485105Z/` and
`build/video-surface-hints-test/120fps-20260928T224912380724Z/`. Both runs retained
all five phases before applying the unchanged throughput gates. Preferences and
ADB mappings were restored, and normal 720p60 playback resumed. No improvement in
physical Late judgments is claimed.

The original overlay probe also passed with the new settling/correlation logic:
59.17–59.41 presentation callbacks/s across all four phases, with 23.26–24.38 ms
mean decode-to-callback timestamp delay. All four compositor samples matched;
the inspected video-only screenshot confirms both control layers were hidden.
Evidence: `build/video-composition-test/60fps-20260928T225217138490Z/`.

### AOT baseline, decoder operating rate and Host cadence, 2026-09-29

The Android build script now supports a separate `-Mode profile` APK. Archive
inspection confirmed AOT `libapp.so` files and no Dart kernel blob; the debug APK
contained `kernel_blob.bin`. The same app ID/development signature preserves
settings through installation. Earlier Flutter measurements above used debug
builds and do not establish release performance. Video tests now identify the
installed app's build type at runtime, including when installation is skipped.

The AOT 720p60 interaction test passed decoded colors, seven synthetic pointers,
Field deltas, settings/calibration isolation and reconnection. Over 21.03 seconds
it received/decoded 60.00 FPS and reported 59.33 presentation callbacks/s. The
last 240-frame receive-to-callback window was 31.41 ms mean / 38.37 ms P95,
including 5.34 ms decode and 24.18 ms post-decode. This did not establish a video
latency improvement over the debug build. Evidence:
`build/video-device-test/720p60-profile-20260928T230742830570Z/`.

The AOT 120 FPS Surface-hint comparison also failed: 34.40–89.73 presentation
callbacks/s across its five phases, despite reception near 120 FPS. Decode rates
varied from 48.74 to 120.18 FPS; per-phase software drops ranged from 0 to 571.
Evidence: `build/video-surface-hints-test/120fps-profile-20260928T230505312033Z/`.
These ordered runs do not prove that AOT makes presentation slower; they show
that changing build mode alone did not resolve the observed failures.

Two additional candidates were tested and **reverted**, not enabled for users:

- On this SM8250/Qualcomm OMX decoder, requesting operating rate 32767 without
  realtime priority, as used on selected devices by
  [Moonlight](https://github.com/moonlight-stream/moonlight-android/blob/master/app/src/main/java/com/limelight/binding/video/MediaCodecHelper.java),
  produced 31.43 / 38.24 ms receive-to-callback mean/P95 at 60 FPS. The 120 FPS
  interaction run timed out before its required frame count; it has a final
  snapshot in `instrumentation.txt`, not a completed steady measurement. There
  was no demonstrated benefit. Evidence: `build/video-device-test/720p60-profile-20260928T231223647442Z/`
  and `720p120-profile-20260928T231432933791Z/` under the same parent.
- Taking the latest capture only at fixed Host deadlines narrowed PC send
  interval P5/P95 from 4.63/13.10 ms to 6.36/10.24 ms at 120 FPS. However, mean
  capture-available-to-send time increased from 2.81 to 5.16 ms. The phone still
  failed: 119.99 receive / 93.61 decode / 73.27 presentation callbacks/s over
  34.08 seconds, with 30.82 / 37.33 ms receive-to-callback mean/P95. PC evidence:
  `build/video-test/720p120-20260928T231722805771Z/` and
  `720p120-20260928T232136138560Z/`; phone evidence:
  `build/video-device-test/720p120-profile-20260928T232254725601Z/`.

The PC test retains per-frame capture/encode/send/receive timestamps in CSV and
reports frame-interval distributions, separately from latency. A failed Android
test that never wrote JSON no longer has its original failure masked by parsing
the shell's missing-file message as JSON. Default playback stays at 60 FPS;
physical Late-judgment verification remains open.

Final verification rebuilt both debug/profile APKs and their instrumentation
packages, with lint and 48 Kotlin tests passing. The restored Windows source
passed all six CTest cases. Running the profile test against an installed debug
app correctly failed with a build-mode mismatch, retaining that failure rather
than a JSON parsing error. After restoring the profile app, the 720p60 test
passed again: 60.00 receive/decode FPS and 59.45 presentation callbacks/s over
11.02 seconds; the last 240-frame receive-to-callback window was 31.46 ms mean /
36.45 ms P95. Preferences were compared byte-for-byte before and after the test;
normal playback and the original ADB mappings were restored. Evidence:
`build/video-device-test/720p60-profile-20260928T233719189760Z/` and
`build/profile-mode-mismatch-run.log`.

### Android buffer presentation traces, 2026-09-29

`tests/video-system-trace.py` records scheduling, graphics/video annotations,
FrameTimeline and the separate `android.surfaceflinger.frame` buffer/fence source
around the existing dry-run USB test. It records on the phone, pulls the trace
after stopping only its own verified Perfetto process, checks preferences and
ADB mappings, and resumes the normal app. The original video acceptance outcome
is retained, including a failing exit code at 120 FPS.

`tests/analyze-video-trace.py` uses the app's codec thread with the most outputs,
excludes its first three and final two seconds, and selects the busiest video
Surface in that interval. This analysis window is distinct from the test's
snapshot-based throughput window. It matches Queue, Latch and
PresentFenceSignaled by exact layer name and buffer frame number. Missing fences,
ambiguous events and invalid ordering cannot become zero-latency samples. All
three stage distributions use the same complete frames. SQL, raw events, matched
frame CSV, import diagnostics and JSON are retained alongside the trace.

| Stream | Analysis window | Queued / complete frame chains | Queue to latch mean | Latch to present mean | Queue to present mean / P95 |
| --- | --- | --- | --- | --- | --- |
| 720p60, final tool verification | 9.92 s | 595 / 578 | 5.14 ms | 11.36 ms | 16.51 / 20.32 ms |
| 720p120 | 19.34 s | 1642 / 1220 | 4.12 ms | 11.45 ms | 15.57 / 19.74 ms |

An earlier complete 60 FPS trace independently matched 608 of 620 queued frames:
5.25 ms queue-to-latch, 11.39 ms latch-to-present and 16.63 / 20.25 ms total
mean/P95. Both analyzed final traces retained a SurfaceFlinger work-duration
counter of 12.33 ms throughout their analysis windows. This is a configured
budget, not measured CPU execution or a proven immutable latency floor. The
roughly 11.4 ms after latch is nevertheless visible in matched OS frame events;
ordinary app-thread runnable waits do not account for that whole stage.

The final 60 FPS USB test passed. The 120 FPS run received 119.98 FPS, decoded
86.18 FPS and reported 62.86 presentation callbacks/s over 20.03 seconds; it
failed the unchanged >=110 gate. Within the separate trace window, 422 queued
buffers had no corresponding latch/present event. This supports investigating
delivery cadence and frame coalescing, but missing trace events alone are not a
physical dropped-frame count. The selected 120 FPS app video thread had a 0.38 ms
runnable-wait P95 and a 12.55 ms maximum; the receive thread had 1.09 / 13.27 ms.
These are individual waits under tracing, not per-frame totals or proof that a
priority change would solve the problem.

Tracing adds overhead: these runs do not replace the untraced performance
baseline. The analyzed traces reported 35 ftrace setup notices and three or four
dropped negative timestamps; their sources have not been resolved. No buffer
overwrite or lost-event counter was reported for these captures. An initial
64 MiB trace did overwrite its beginning and is excluded from the table. The
final configuration separates metadata from a 128 MiB event buffer.

The buffer source follows [AOSP FrameTracer](https://android.googlesource.com/platform/frameworks/native/+/9e84f339bf/services/surfaceflinger/FrameTracer/FrameTracer.h).
Its present fence is still an OS timestamp, not a camera measurement of the panel.
Dequeue timestamps and the parser's derived acquire fields are not used: decoder
output buffers may be reserved well before a frame arrives, and missing acquire
events produced invalid derived durations in this trace. No cross-device clocks
are subtracted, no system display properties were changed, and no runtime tuning
or physical Late-judgment improvement is claimed.

Evidence under `build/video-system-trace/`:
`60fps-profile-20260929T000519834099Z/` (final capture/analysis flow),
`120fps-profile-20260928T235619752235Z/`, and
`60fps-profile-20260928T235416831692Z/` (earlier complete frame trace).
Two frame-correlation regression tests passed, including missing fences across
different layers, duplicate observations, ambiguous queues and invalid ordering.

### Callback-driven decoder, 2026-09-29

The Android decoder now consumes input/output buffer callbacks and wakes its worker
when a codec buffer or compressed packet becomes available. This removes the former
1 ms polling sleep. Queue capacity, two-frame decoder submission limit, presentation
timestamps, codec priority and the default 60 FPS profile are unchanged.

Flush suppresses stale buffer indices until a barrier on the codec callback looper
has drained, then restarts the asynchronous codec. Errors remain fatal across flush;
late callbacks cannot revive a closed session. Input buffer access is serialized
with error handling to avoid copying through invalidated direct buffers. This follows
the lifecycle requirements in [MediaCodec](https://developer.android.com/reference/android/media/MediaCodec)
and the error/flush handling rationale in
[AndroidX Media3](https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/mediacodec/AsynchronousMediaCodecCallback.java).
Three additional JVM tests cover these ownership boundaries; all 51 Kotlin tests
and Android lint passed.

Two untraced old/new comparisons used the same phone, profile build, 720p60 stream,
120 Hz display and existing dry-run interaction test. Each steady interval lasted
approximately 21 seconds. Latencies below cover the final 240 matched frames:

| Run | Receive to submit mean | Decode mean | Decode to callback timestamp mean | Receive to callback timestamp mean / P95 | Present callbacks/s |
| --- | --- | --- | --- | --- | --- |
| Polling baseline, first | 2.09 ms | 5.29 ms | 23.87 ms | 31.25 / 36.22 ms | 59.18 |
| Callback candidate, first | 1.02 ms | 5.25 ms | 23.99 ms | 30.26 / 35.63 ms | 59.23 |
| Polling baseline, repeat | 1.64 ms | 5.25 ms | 23.84 ms | 30.73 / 36.60 ms | 58.52 |
| Callback candidate, repeat | 1.02 ms | 5.27 ms | 23.75 ms | 30.04 / 35.65 ms | 59.07 |

Both comparisons reduced receive-to-submit by 0.62–1.07 ms and total local callback
timestamp latency by 0.69–0.99 ms. This supports retaining the narrower scheduling
change, not a claim of resolved Late judgments or a measured CPU/power improvement.
Post-decode presentation remains the larger stage. Every 60 FPS run passed colors,
seven synthetic pointers, Field deltas, settings/calibration isolation and reconnect;
each had 29 session drops including startup and zero unmatched callbacks.

The candidate's 120 FPS run still failed the unchanged >=110 presentation gate:
119.98 receive / 116.49 decode / 86.89 presentation callbacks/s over 14.05 seconds.
Its final 240-frame receive-to-callback window was 28.02 ms mean / 35.81 ms P95,
with 581 session drops and zero unmatched callbacks. Colors and reconnect completed,
but high-rate playback remains experimental. These figures do not isolate a
120 FPS regression or improvement against older runs with different conditions.
Phone preferences and ADB mappings were verified unchanged after every run.

The same candidate also passed the full 1080p60 interaction/reconnect test:
60.01 receive / 59.97 decode / 59.35 presentation callbacks/s over 21.11 seconds,
with 31.72 ms mean / 37.02 ms P95 receive-to-callback timestamp latency over the
final 240 frames. Its input wait, decode and post-decode means were 1.18, 6.75
and 23.79 ms. This is compatibility validation, not a paired 1080p improvement.

Both debug/profile APKs and their instrumentation packages were rebuilt. The
installed profile APK was verified against the delivered file's SHA-256, with
the normal Host connection resumed and original phone preferences preserved.

Evidence: `build/async-codec-baseline/video-60/` and, under
`build/async-codec-test/`, `candidate-60-20260929T002303139549Z/`,
`baseline-60-repeat-20260929T002943313910Z/`,
`candidate-60-repeat-20260929T003022249974Z/`,
`candidate-120-20260929T002741916259Z/` and
`candidate-1080p60-20260929T003117081032Z/`.

### Presentation cadence experiments, 2026-09-29

A fresh trace of the callback-driven decoder matched 1346 complete presentations
from 1807 queued buffers over its 16.40-second analysis window. Queue-to-present
averaged 15.75 ms, including 11.48 ms after latch. Queue interval P5/median/P95
was approximately 1.83/7.53/16.81 ms; complete presentation intervals were
approximately 8.20/8.27/16.57 ms. Burst delivery and missed refresh intervals
remain visible. Missing frame events are not counted as physical drops.

Two presentation candidates were built and tested, then **reverted**:

| 720p120 candidate, untraced | Receive / decode FPS | Present callbacks/s | Receive to callback timestamp mean / P95 |
| --- | --- | --- | --- |
| Zero render timestamp | 119.99 / 119.91 | 90.36 | 29.72 / 34.76 ms |
| Choreographer release, at most two decoded frames waiting | 119.96 / 119.96 | 89.69 | 37.66 / 46.33 ms |

Both steady intervals lasted 14.04 seconds; latency uses each run's final 240
matched callbacks. Neither passed the unchanged >=110 presentation gate. The
second candidate added waiting without achieving the required throughput.
Decoded colors, settings/calibration interaction and reconnect completed; separate
inspection of both saved Host traces confirmed six balanced key holds and exactly
the expected +600/-300 relative Field deltas. All device preferences and ADB mappings
were restored. No physical gameplay improvement is inferred from these tests.

The zero-timestamp experiment follows an alternative in
[Moonlight's renderer](https://github.com/moonlight-stream/moonlight-android/blob/master/app/src/main/java/com/limelight/binding/video/MediaCodecDecoderRenderer.java).
It is not a general way to disable SurfaceView frame loss: Android documents
[excess-frame dropping for View surfaces](https://developer.android.com/reference/android/media/MediaCodec#using-an-output-surface).
The synchronization candidate used the callback thread's Choreographer and one
submission per consumed pulse; its timestamp was not corrected for the display's
1 ms app-vsync offset. This tests that implementation, not all possible pacing policies.

The latter candidate's trace recorded 2463 Choreographer callback entries at
120.97/s over a 20.35-second callback span. Entry interval P95 was 9.89 ms, so
the trace does not prove every deadline was met. In the separate 20.36-second
buffer window, 1356 of 1765 queued buffers had complete presentation chains.
A nearby display dump reported 120 Hz rendering and no frame-rate overrides.
These observations do not support a fixed 90 FPS application cap in this run.
The buffer window includes interaction/recovery and must not be compared directly
with the test's 14.04-second steady FPS interval. Tracing also adds overhead.
Import diagnostics retained one/eight negative-timestamp drops for the baseline/
paced trace respectively, and 35 ftrace setup notices each.

`analyze-video-trace.py` now retains queue/presentation interval distributions and
per-thread Choreographer callback entry cadence, together with the raw callback
CSV/SQL. Duplicate timestamps cannot inflate these rates; missing/single events
report no interval or rate. All four analysis regression tests passed.
The previously verified profile APK was reinstalled and its SHA-256 matched
the restored delivery file. Production decoder code and the default 60 FPS profile
remain at the callback-driven implementation above.

Evidence under `build/async-codec-test/`: `sequential-120-20260929T004334913441Z/`
and `paced-120-20260929T005153962140Z/`. Traces under `build/video-system-trace/`:
`120fps-profile-20260929T003904916026Z/` and `120fps-profile-20260929T005343760156Z/`.
The display snapshot is `build/paced-display-state.txt`; discarded source patches
and APKs are retained under `build/surface-sequential-*` and `build/surface-paced-*`.

### Android streaming lifecycle, 2026-09-29

The profile build passed three repetitions each of actual `Activity.recreate()`,
task background/resume and SurfaceView GONE/VISIBLE transitions at 720p60.
The helper observes Android lifecycle stages: recreation must destroy the old
Activity and resume a different instance; returning from the background must
resume the same instance. Surface-only teardown keeps the input connection alive.

Every transition starts with six held keys and one Field owner. The independent
Host trace contains exactly ten alternating DOWN/UP pairs per lane and ten
300-pixel relative movements, including a fresh gesture after the last recovery.
Old MOVE/UP events after each recovery produce no additional Host input.

All nine recoveries reached 60 presented frames with the expected format and
ready input. That milestone took 2.23-2.29 seconds after Surface/Activity recovery
and 2.97-3.09 seconds including task resume; these values include accumulating
60 frames and are not first-frame or input-release latency measurements.
Each active session had one worker for each of the five input/video roles.
Teardown removed all video workers and both observed native codec threads;
Surface-only teardown retained exactly the two input workers.

Socket snapshots were constant per phase: 16 while playing, 15 with the Surface
hidden, 13 while backgrounded and 14 after Activity recreation/disconnection.
Open descriptor counts varied: 251-262 while playing, with disconnected warmup
203 and final 208. These finite snapshots establish neither a full process/GPU
memory-leak check nor long-session stability. Physical rotation, cable removal,
digitizer input and full charts still require separate acceptance.

Evidence: `build/video-device-test/lifecycle-720p60-profile-20260929T012251966050Z/`.
The runner preserved completed-phase metrics, verified Host traces, restored ADB
mappings and byte-identical phone settings, then reopened the app. The profile
build, all 51 Kotlin tests and lint passed. Production input/video code is unchanged.

The existing color/seven-pointer/settings/reconnect regression also passed using
the shared helper: 11.05 seconds steady receive/decode/present callbacks
60.00/59.91/59.46 FPS, with zero untracked callbacks. Its last 240 local frames
measured receive-to-codec-reported-presentation mean 31.25 ms / P95 36.81 ms;
the end-of-run input RTT sample was 3.41 ms. This is a regression result, not a new
latency improvement or physical touch-to-photon measurement. Evidence:
`build/async-codec-test/lifecycle-regression-60-20260929T012421894227Z/`.

### PC window recovery, 2026-09-29

`tests/video-recovery.py` controls only its own background D3D test window and
uses isolated PC ports without changing ADB mappings. At 720p output, resizing
the source from 1280 x 720 to 960 x 540, 900 x 900 and 1280 x 480, minimizing
for 2.07 seconds, restoring and returning to the original size all passed.
Fourteen decoded frames retained six correct colors, centered black bars and
a moving marker. Each visible phase received 61 frames in its final second.

Minimization triggered the existing encoder-stall restart. The test receiver
reconnected with fresh SPS/PPS and an IDR; this is recovery through reconnection,
not proof that the original socket remained open. A second deliberate video
disconnect also recovered. Concurrent input retained all six holds until explicit
release: 257 ACKs, median/max local RTT 0.143/0.669 ms and maximum ACK gap 95.70 ms.
Evidence: `build/video-recovery-test/720p-20260928T191058477482Z/`.
This run used a PC software decoder for pixel inspection. Android recovery,
actual-game resizing, 1080p recovery and GPU device loss remain separate checks.

### Earlier Flutter throughput baseline, 2026-09-29

Both resolutions use a project-owned moving Direct3D pattern at the corresponding
native client size. The 1080p PC run delivered 602 frames in 10.012 seconds
(60.13 FPS), with 21 IDRs. A separate ffprobe inspection confirmed 1920 x 1080
Constrained Baseline H.264 and no B frames; throughput is measured from packet
arrival times, not raw-bitstream frame-rate estimates. PC available-to-encode
mean was 5.32 ms. Concurrent local input RTT median/max was 0.156/0.908 ms.

| USB format | Steady interval | Receive FPS | Present callbacks/s | Final queue depth | Session drops |
| --- | --- | --- | --- | --- | --- |
| 1280 x 720 | 10.006 s | 59.96 | 59.96 | 0 | 29 |
| 1920 x 1080 | 11.010 s | 60.04 | 59.86 | 1 | 30 |

Dimensions are checked against the received headers on the phone, as well as the
PC wire stream and source window. Six decoded color bands, relative Field motion
while six keys remain held, settings/calibration input isolation and reconnection
passed at both sizes. Reconnected sessions presented 85 and 115 frames respectively.
The last local receive-to-present samples were 25.35/25.96 ms; these are individual
stage samples, not percentiles or physical touch-to-photon measurements. Drops
include startup recovery. These short pattern runs do not establish thermal or
busy-chart performance.

Artifacts: `build/video-test/1080p-20260928T184417231858Z/`,
`build/video-device-test/720p-20260928T184645008997Z/` and
`build/video-device-test/1080p-20260928T184558664839Z/`.
New pattern runs retain their evidence in resolution/time-named subdirectories.

### Earlier baselines

Cooperative broadcast test (0.5): six healthy receivers sustained 60.12–60.23 FPS
while a seventh receiver stopped consuming. The test matched 332 capture/encode
timestamp pairs across viewers, confirming reuse of the same encoded frames.
The stalled viewer was isolated. This is seven PC TCP clients, not seven USB phones;
hub bandwidth, power and simultaneous physical-phone latency remain unverified.

The following single-phone throughput figures are the earlier 0.4 native-UI baseline;
they must not be treated as performance measurements of the final Flutter surface.

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

- Flutter now keeps a full-screen texture at a fixed size across settings transitions.
  Native touch routing uses only the actual settings-entry rectangle while playing;
  all gameplay pointers stay in the native View. The app and settings content fill
  the screen, while video keeps an independent aspect-ratio transform.
- USB actions moved into the settings Connection page. Fixed action bounds and
  centered labels prevent connection-state text from shifting surrounding controls.
  The small settings entry requires two taps within two seconds, with a first-tap
  Android system Toast, verified through an Android Toast accessibility event. The
  return button stays at the top left. The obsolete device test requiring the
  return button at the opposite corner was removed; the earlier 10/11 device
  result remains historical and is not a pass for the current UI. Gameplay shapes are unchanged.
- Settings save automatically with serialized writes and coalesced slider edits;
  leaving settings and backgrounding flush pending changes. Failed writes retain
  edits for retry. Defaults is at the top right and preserves language and theme.
  The sidebar is Connection, Touch, Controls, Picture, Other. Other contains
  English/Chinese and Light/Dark/System appearance, with Dark as the initial preference.
- Transient calibration, validation and persistence feedback uses Android system
  Toasts in the selected app language. Settings error banners were removed. Page
  navigation does not cancel an error Toast; background-generated messages wait
  for resume. Both language resources remain packaged for offline switching.
- USB status/action alignment stays fixed while a compact inset log retains the
  latest outcome through retries. Content height transitions are animated unless
  reduced motion is requested; empty metrics no longer reserve space. Defaults
  requires an explicit dialog confirmation; Cancel and system Back preserve settings.
- USB logs are integrated into the USB status/action card; automatic discovery is
  separate. Partial key selections pack in lane order at the center without resizing
  their shapes. Hit testing uses the translated regions and original lane IDs.
- Each phone may select any control subset, including none. There is no required
  assignment sum. Shared-key holders are reference counted; one disconnect or
  watchdog timeout cannot release another phone's hold. Field ownership is queued.
- Saved IF key changes release old holds, synchronize labels and scans, and require
  fresh touches. Unsupported/malformed bindings pause input without editing game saves.

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
latency still need gameplay testing. Long thermal/stability and busy-chart 1080p60 runs,
multi-monitor capture, window resize/device-loss recovery and physical
glass-to-glass latency remain unverified. Normal-chart probes on 2026-09-29
confirmed that the former default absolute mapping can leave the game's Field marker at
different positions for the same normalized request. A visible-edge homing attempt
also retained an offset. The game locks the OS cursor; its coordinates cannot prove
touch-to-game alignment. [Field investigation](FIELD-MAPPING.md) records the valid
observations, excluded menu runs, replacement direct-positioning checks and next
acceptance criteria. Relative remains the default; both modes still need systematic
physical acceptance. Absolute is currently limited to the verified game binary.
See `tests/manual-acceptance.md` for the remaining physical checks.

## Real-game and native input evidence

- Official Steam instructions and the installed tutorial distinguish keyboard
  lower notes from mouse upper Field. Central lanes are ASDF; outer lanes are
  Shift/Space with slanted judgment lines. The preset follows this observed 16:9 layout.
- Earlier tutorial-window video interval: 10.021 s, receive 59.98 FPS, present callbacks
  57.28 FPS, queue depth 0, 29 session drops including startup. Last local samples:
  PC available-to-encode 3.08 ms, decoder 6.44 ms, receive-to-present 35.16 ms.
- In that earlier run, panel/app mode was 120 Hz, requested 120 Hz, Choreographer callbacks 119.65 Hz.
  Earlier system-selected mode was also 120 Hz; the preference is not claimed to
  improve this device's already-selected rate. Source video remains 60 FPS.
- `build/game-video-test/` contains metrics, full-frame and pressed screenshots,
  and exactly one DOWN/UP pair for each of six enlarged keys plus Field transport.
  Windows input is dry-run for this capture/feedback test.
- An earlier build was also checked against the game's animated results screen:
  10.014 s, receive 60.02 FPS, present callbacks 57.42 FPS, queue depth 0 and 29
  session drops including startup. Panel/app mode remained 120 Hz; Choreographer
  callbacks averaged 110.91 Hz, so this is not a claim of sustained 120 callbacks/s.
  Fit displayed the complete 720p stream at `(240, 0, 1920, 1080)` on the phone.
  The trace verified six key holds/releases and one relative Field movement.
  `build/game-video-relative-20260929/` contains this run's metrics and inspected
  pressed-feedback screenshot. All Windows input was dry-run, and the results
  page does not establish gameplay alignment or busy-chart performance.
- `build/game-input-test/lane-1-6.txt`: phone USB Shift+Space passed; visual inspection
  confirmed the actual game entered its tutorial. No full-song performance is claimed.
- `build/native-input-test/metrics-focus.json`: physical client coordinates match
  at left/center/right; focus-loss release observed in 34.13 ms by the test driver.
  Separate silent-connection watchdog observation was 507.37 ms. These are samples,
  not hard OS scheduling bounds or game latency measurements.
- The native key receiver observed Shift/A/S/D/F/Space scans 42/30/31/32/33/57.
  An IME initially changed key processing; testing with plain US layout resolved it.
  Host now prints the target keyboard layout and warns for CJK IME layouts.
