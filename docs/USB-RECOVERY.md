# USB recovery and physical touch checks

Recorded on 2026-09-29 with Windows 11, ADB 37.0.1 and the connected Xiaomi
22021211RC running Android 14. Physical gameplay and long-term stability remain open.

## Connection changes

- Host discovers authorized physical USB phones and maintains control/video reverse
  forwarding on ports 27184/27183. Correct mappings are retained, missing mappings
  are created, and conflicting mappings are not overwritten.
- Recovery runs independently of input and encoding. ADB commands have bounded
  output, timeouts and shutdown cancellation. Production recovery never kills the
  shared ADB server. `--no-usb` leaves forwarding to external tools.
- Windows builds include ADB, its two DLLs and its license in `dist/platform-tools`.
  Classic console QuickEdit is disabled to avoid console-selection pauses.
- When starting an ADB server, Host defaults `ADB_USB_LEGACY` to `1` inside its own
  process. Existing servers and explicit environment overrides remain unchanged.
  The option is documented in the [official release notes](https://developer.android.com/tools/releases/platform-tools).

## Checks

MSVC Release build and all eight CTest suites passed. An isolated dry-run Host
received actual phone ACK/input checks after these transitions:

| Transition | Forwarding available | ACK and input check completed |
| --- | --- | --- |
| First automatic setup | 884 ms | 2.40 s |
| Deleted forwarding | 1.15 s | 2.73 s |
| Host restart with existing mapping | 3 ms | 1.42 s |
| ADB server restart | 6.67 s | 8.24 s |

These are recovery durations, not touch or video latency measurements. Each phone
check verified multiple acknowledgements, six-key/Field messages and release on
disconnect. A separate `adb reconnect` phase failed to rediscover the phone within
25 seconds; it is not counted as passed. Physical unplug/replug was not tested.
The phone and normal forwarding were restored after testing.

Normal use then produced USB read failures at 11:43:56 and 11:51:23; the user
confirmed there had been no unplugging or USB setting changes. Host rebuilt the
lost mappings, but that alone does not prevent underlying USB failures.
At 11:56 the server was switched from `LIBADBUSB` to `NATIVE`, confirmed by
`adb server-status`. No further read failures were observed during the subsequent
short session. ADB backend compatibility is a working diagnosis, not proof that
all intermittent disconnections are eliminated.

Local evidence is in `build/usb-recovery-test/results.json`,
`build/usb-recovery-final-build.log`, `build/usb-host-out.txt` and
`build/usb-legacy-host-out.txt`. Generated evidence and device system files are not
committed.

## Two-finger cancellation after holding a key

The user's later reproduction exposed a separate fault: hold one ground key, tap
another ground key, and the held key releases when the tapping finger lifts.
Six physical reproductions showed Android's window receiver logging
`ACTION_POINTER_UP(1)`, followed by MIUI `CatcherInputEventListener` extracting the
controller's accessibility label and changing that event to `ACTION_CANCEL` before
it reached the Activity. The remaining finger's later `ACTION_UP` still arrived.
This was not a USB disconnect or a pointer-index/reference-count error.

`ControllerView` now supplies a localized accessibility input hint while retaining
its accessibility label. Read-only inspection of this phone's
`ViewContentFetcher.fetchNativeViewContent` confirmed that MIUI skips nodes with
input hints. No content-picker or gesture setting is changed, and genuine
`ACTION_CANCEL` still releases input.

The regression injects a 900 ms hold and three short taps through the actual phone
window into a recording sink, without sending game input to the PC. Before the
fix, it failed with `D3, D4, CLEAR`; after the fix, all three `D4, U4` pairs arrive
and key 3 releases only on its own UP. All seven native touch tests passed.
The user confirmed that physical two-finger input is now normal; the separate
three-finger cancellation still reproduced on the repaired APK.
Evidence: `build/input-trace/two-finger-before.txt`, `touch-after.txt`,
`activity-app.txt`, `activity-kernel.txt` and `activity-all-app-log.txt`.

## Three-finger cancellation

The profile APK, 20 touch unit tests, Android lint and six native MotionEvent tests
passed. The new test holds two ground keys before a third finger enters Field,
reorders pointer indices, slides across the menu region, lifts/re-touches Field,
and verifies independent key releases. Synthetic delivery does not validate
system gesture interception.

During real touches, the application received `ACTION_CANCEL` while the hardware
still reported three active touch slots. For example, at device uptime
3902303.744 s, three pointers were cancelled and remained present in the kernel
trace afterwards. There was no simultaneous connection/input-gate or geometry
change in the application trace.

Read-only inspection of this phone's `miui-services.jar` showed that
`MiuiMultiFingerGestureManager.gestureStatusIsReady` calls `pilferPointers` as soon
as a three-finger gesture enters `DETECTING`, before its final action succeeds.
Therefore the absence of a screenshot or split-screen UI does not exclude system
gesture interception. This path exposes no ordinary application-window opt-out.
The user then isolated **three-finger swipe to screenshot** and confirmed that
turning it off restores normal two- and three-finger operation, including two held
ground keys plus Field sliding. A read-only check afterward returned `none` for
`three_gesture_down`. The user made the system-setting change; the tools did not.
This confirms the gesture conflict but does not meet the user's preference to keep
the screenshot shortcut enabled. Genuine `ACTION_CANCEL` continues to release input.

An experimental ADB touch relay initially failed to connect over a local socket.
An authenticated loopback revision subsequently opened the physical touchscreen
and passed 12 device tests, but the user still reproduced cancellation with the
screenshot shortcut enabled. The experiment was withdrawn at the user's request.
The Host and phone were restored to the prior versions, retaining the tested
two-finger fix. The restored APK has SHA-256
`EFC4E7D583210145B04D1D4D157AF28ACBB509F9A66E24B969C5AC3223548564`.

Opt-in diagnostics compare Activity-entry and native gameplay pointer transitions,
throttle multi-pointer moves and record input cancellation reasons. Activity-entry
logging is disabled while a settings or calibration page is open:

```powershell
adb shell setprop log.tag.InFalsusTouchInput DEBUG
adb logcat -v threadtime InFalsusTouchInput:D '*:S'
# Turn off diagnostics when the check is finished.
adb shell setprop log.tag.InFalsusTouchInput INFO
```

Physical trace evidence is in `build/input-trace/`; the input source and system
framework were only read. The tools did not modify screenshot, split-screen or
other gesture settings.
