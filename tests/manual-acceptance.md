# Phase 1 physical-device acceptance

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

Video, FPS stability and physical touch-to-photon latency are Phase 2/3 gates.
