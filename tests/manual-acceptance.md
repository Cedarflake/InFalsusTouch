# Physical-device and game acceptance

Status: physical gameplay checklist not executed. Android 14 device instrumentation
and real USB transport to a dry-run Windows host passed on 2026-09-28; see
`docs/STATUS.md`. These automated checks do not replace physical gameplay tests.

## Setup

- Build both artifacts, connect an authorized USB phone and run setup-adb.ps1.
- Select the running In Falsus window, open the app and focus the game.
- Use the game's configured keys: Left Shift, A, S, D, F, Space.

## Touch and input

- Each lane triggers its matching key; release ends that key immediately.
- Two-, three- and six-key chords work; long holds survive other fingers tapping.
- Put two fingers on one lane, lift one, confirm the remaining hold continues.
- Slide a lane finger across all other lanes: ownership must stay on the origin.
- Slide a Field finger continuously while other fingers hold and tap lanes.
- Add a second Field finger: it must not steal control. Lift the owner: the
  second finger must not be promoted until it is lifted and pressed again.
- Rapidly tap the same lane and alternating lanes; confirm no missed UP/stuck key.
- Confirm left/center/right Field mapping at different window positions, DPI
  scales and negative-origin monitors, including after moving the game window.
- Test Relative mode and the sensitivity/acceleration/smoothing/speed options.

## Lifecycle and failure

- Hold all six lanes, disconnect in the app: verify all keys are released.
- Repeat while unplugging USB, closing the Host with Ctrl+C and backgrounding
  the app. Record detection times; silent dead links use the 500 ms watchdog.
- Hold keys, switch PC foreground window: no input should continue there.
- Return to the game: old fingers must be lifted before fresh input is accepted.
- Reconnect, including after restarting ADB/reverses: no old holds are replayed.
- Rotate between the two landscape orientations while holding: state clears.
- Open Android system UI / lose Activity focus: held input is cleared.
- Close or minimize the selected game: release occurs and no new input is injected.
- Try mismatched process privileges: failures must produce explicit diagnostics.

## Record

| Item | Evidence |
| --- | --- |
| Phone / simultaneous pointer capacity | Not tested |
| Android / Windows / In Falsus version | Android 14/API 34; game version not tested |
| Six-key + Field simultaneous operation | Not tested |
| USB disconnect release timing | Not tested |
| Real SendInput accepted by game | Not tested |
| Measured control RTT (method/sample count) | Not tested |

## Video and sustained gameplay

The automated video test covers a Direct3D color pattern, WGC, hardware H.264,
real USB, MediaCodec output pixels, seven synthetic pointers and reconnect. It
does not substitute for the following checks with In Falsus:

- Play varied, visually busy charts for at least 15 minutes at 720p60. Record
  receive/decode/present FPS, phone drops, queue depth, input RTT and thermals.
- Confirm long holds and rapid chords remain reliable while video is busy.
- Minimize, restore, resize and move the game between monitors; the capture must
  recover and Field coordinates must continue matching the selected client area.
- Disconnect/reconnect USB with both video and keys active; re-run ADB setup if
  the reverse mapping is gone. No held input may be replayed.
- Background/resume, rotate and recreate the phone Surface repeatedly. Confirm
  codec resources and socket counts stay bounded.
- Test 1080p60 and bitrate/FPS alternatives on devices supporting those formats.
- Test unsupported codecs and GPU/device-loss failures: video errors should be
  explicit and must not block input cleanup.
- Use high-speed external recording for physical touch-to-photon latency. Do not
  infer it by adding timestamps from unsynchronized PC and phone clocks.
