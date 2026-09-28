# Field mapping investigation

## Current result

On 2026-09-29, the replacement direct-positioning Host passed a normal-chart
game-state test: down/up at 0.25, 0.5, 0.75, 0, 1, 0.25 and 0.5 reached the requested
positions, followed by 240 target updates at 120 events/second and a final midpoint.
The largest settled error was 0.000317 of the Field range; the moving sweep's
largest target/readback difference was 0.02110. This verifies game-state positioning,
not physical phone-to-picture alignment or touch-to-photon latency. The user reported
improved hand feel after the input changes; full physical acceptance remains open.

Relative remains the default and now preserves actual horizontal View-pixel
displacement. Absolute is selectable for mobile-style down-to-position and tracking
in the supported game build. Neither mode fixes game sensitivity at 1.8.

## Direct game positioning

The inspected Windows In Falsus 1.0.4b build accumulates input as:

```text
rawPosition += relativeMouseUnits / primaryDisplaySystemWidth * effectiveSensitivity
visiblePosition = clamp(rawPosition, 0, 1)
```

Raw position may lie outside the visible range, so the visible edge cannot be
used as a new origin. The Host reads raw position and effective sensitivity through
`ReadProcessMemory`, then sends an ordinary relative `SendInput` correction to the
requested normalized coordinate. It does not patch the game, write its memory,
change its settings or inject a library. Sensitivity is read for every correction;
1.8 was the effective value in this run, not a constant in the control algorithm.

The reader accepts only the inspected `GameAssembly.dll` SHA-256
`ab1d8fa7739078510fab5f8580095c90ea0f5e9236ac9b918e934cb9ae1b7d9c`.
The GameScene and Field objects are resolved at runtime and samples are checked for
stable pointers, finite values and consistency between raw and visible position.
Unknown binaries disable direct gameplay positioning instead of guessing offsets.
Relative input remains available. A game update requires fresh verification.

Only one mouse correction is pending at a time. New finger coordinates replace the
target while the Host waits for game feedback, preventing a backlog of duplicated
movement. Normal UP completes the final sample; cancel, disconnect and loss of
foreground cancel future corrections. Missing feedback or ignored input stops the
gesture after 100 ms. The selected game must be foreground with its cursor locked;
unlocked menus retain OS cursor positioning.

Production-path evidence:
`build/game-input-test/direct-field-20260928T200723742918Z.json` and its Host log.
The display width was 2560, effective sensitivity 1.8. Settled request-to-state
readbacks were 5.00–11.92 ms; these exclude phone, USB, video and physical display.
The pure controller suite also verifies sensitivity changes, offscreen raw positions,
rapid retouch, ignored input and cancellation. Physical mouse concurrency, multi-phone
handoff and other display arrangements still need real-game checks.

Reproduce only in a foreground normal chart, without concurrent physical input:

```powershell
uv run --python 3.13 tests/direct-field-game.py --window 0xHANDLE
```

The test fails before input on unsupported binaries or unlocked/nonforeground windows.
It retains failed-run observations after starting a session and does not alter ADB
reverse mappings or the normal Host. Game-state feedback is read-only.

## Earlier OS-absolute result

The previous default `SendInput` absolute-client mapping does not reliably select a fixed
position on In Falsus's Field. A normal-chart test on 2026-09-29 reproduced
different visible Field positions for the same normalized input. Adjusting only
the two mapping endpoints is insufficient.

Following the tablet trial, Relative became the default gameplay path, with
sensitivity managed in In Falsus. The later direct-position implementation above
replaces the failed OS-absolute mechanism; it does not turn those earlier tests into passes.

## Relative input decision and source comparison

Sources were inspected on 2026-09-29 at the revisions linked below:

- [InFalsusTouchTool Android](https://github.com/billma007/InFalsusTouchTool/blob/0dfb8caf2294bfab3f771489cf13655e99a4a9c1/android/app/src/main/java/com/billma007/infalsustouch/FullMappingActivity.kt#L220-L225)
  sends horizontal touch deltas with a hard-coded 2x multiplier. Its PC receiver
  truncates to integer relative movement. The separate absolute path uses Windows
  cursor positioning, not game Field feedback.
- [Moonlight Android](https://github.com/moonlight-stream/moonlight-android/blob/b48494cb96bff23d8886c4775cc4f39a1075495d/app/src/main/java/com/limelight/binding/input/touch/RelativeTouchContext.java#L149-L153)
  scales relative touch movement using reference dimensions. Its
  [caller supplies 1280 x 720](https://github.com/moonlight-stream/moonlight-android/blob/b48494cb96bff23d8886c4775cc4f39a1075495d/app/src/main/java/com/limelight/Game.java#L101-L102),
  and movement rounded to zero does not advance the previous touch coordinate.
- [Moonlight's public input API](https://github.com/moonlight-stream/moonlight-common-c/blob/f900dd4767759c7b9d0e93bcea666b55c69ea62f/src/Limelight.h#L572-L610)
  explicitly cautions about absolute mouse compatibility in games. Its virtual
  cursor conversion also retains those limitations; it cannot infer a game's
  internal position. Its relative transport preserves accumulated displacement.
- [Sunshine](https://github.com/LizardByte/Sunshine/blob/8ed7f5bc51eb0e33b779abb77246caf179885a2b/src/input.cpp#L737-L743)
  passes received relative deltas to its platform backend. The inspected revision
  uses [libvirtualhid mouse helpers](https://github.com/LizardByte/Sunshine/blob/8ed7f5bc51eb0e33b779abb77246caf179885a2b/src/platform/virtualhid_input.cpp#L797-L815)
  with distinct relative/absolute operations. Its
  [Windows documentation](https://github.com/LizardByte/Sunshine/blob/8ed7f5bc51eb0e33b779abb77246caf179885a2b/docs/getting_started.md)
  distinguishes driver-backed relative Raw Input from absolute Windows injection.

The first revision used 1280 mouse units per Field touch span. Phone testing
showed this reduced movement on the 2400-pixel display and made calibration affect
speed. Android now preserves horizontal View pixels, including movement outside
the calibrated Field edges while a finger owns Field. The v2 wire still uses
1280-unit blocks; larger swipes split into ordered packets rather than being capped.
Fractional mouse units accumulate within a gesture. Host sensitivity, acceleration,
smoothing and speed limiting remain removed.

The existing single Field owner, immediate native
touch handling and ordered USB queue remain in use. No reference-project code or
driver is incorporated. SendInput is still the Windows backend. Relative input does
not depend on game feedback; only the direct-positioning path reads game state.

## Test conditions

- Installed In Falsus 1.0.4b, normal **Be There / MIN 3** chart.
- Physical client rectangle: origin `(77, 84)`, size `1280 x 720`; window DPI 120.
- Saved game mouse sensitivity: `1.20`; the test did not change this setting.
- Host before the relative-input change: `--no-profile --no-video`, default
  Field/relative parameters (including the former speed cap), one local TCP
  diagnostic client. This isolates mouse injection from USB/video timing.
- Every valid movement received ACK status `4`, meaning this client owned Field.
- The game remained foreground during the recorded movements. Its cursor clip
  rectangle was `(717, 444, 717, 444)`, while `GetCursorPos` stayed near that point.
- Keyboard layout is recorded per run; mouse-only probes did not send lane keys.

The earlier USB Shift+Space tutorial-start test was also rerun successfully, with
the US English keyboard selected before sending that chord. This is separate
from the mouse-only chart investigation.

## Observed behavior

The X positions below are approximate visual readings from the displayed
1026-pixel-wide game-window captures, including the window frame. They describe
the purple **Field marker**, not the white UI pointer or the OS cursor. They are
counterexamples to correct alignment, not pixel-accuracy measurements.

| Sequence | Visible result |
| --- | --- |
| Absolute `0.25`, then four more `0.25` events | Field stayed near X 265 |
| Absolute `1.0` after that | Field moved to about X 528, not its right limit |
| New chart, absolute `0.5` | Field moved from the center to about X 675 |
| Relative `-0.25`, then absolute `0.5` again | Field moved to about X 579 and stayed there; the identical absolute request did not restore the previous position |
| New chart, ten relative `-0.25` events, then two `+0.4166667` events | Field remained at the visible left limit, about X 188 |
| Four more `+0.4166667` events | Field returned to about X 512 |

The last two sequences reject a simple “move far left, then offset from that edge”
calibration: the visible edge did not provide a reliable new position reference.
These historical observations suggested accumulated input and an invisible offset.
The subsequent read-only investigation established the accumulation and clamping
rules described above.

Unity's [mouse documentation](https://docs.unity3d.com/Packages/com.unity.inputsystem@1.14/manual/Mouse.html#cursor-warping)
describes cursor recentering while locked. That explains why an OS cursor reading
is insufficient; it does not establish how this game calculates its Field position.

## Reproducing and interpreting probes

Use the current window handle from `InFalsusTouchHost.exe --list`. Start a normal
chart, keep it foreground, and avoid moving the physical mouse during each probe.
Tutorial demonstration animations and result/menu screens are not alignment evidence.

```powershell
# Replace the handle with the current game's handle.
uv run --python 3.13 tests/game-field.py --window 0xHANDLE --position 0.5
uv run --python 3.13 tests/game-field.py --window 0xHANDLE --delta -0.25
uv run --python 3.13 tests/game-field.py --window 0xHANDLE --position 0.5

# Ordered positions can share one connection; repeat repeats the entire sequence.
uv run --python 3.13 tests/game-field.py --window 0xHANDLE --position 0 0.5 1 0.5
```

The probe now checks focus and cursor locking before injection and while sampling.
It refuses an unlocked menu/result screen by default. `--allow-unlocked` is only
for intentional OS/menu diagnostics and does not prove gameplay mapping.
The initial unlocked-screen rejection was verified on the actual results page.

Each run writes a separate UTC-named JSON and Host log under `build/game-input-test/`.
The JSON records client bounds, DPI, keyboard layout, ownership ACKs, cursor clip
rectangles, and cursor samples immediately after ACK and at 10/50/200 ms. Failed
runs retain partial observations. `transportCompleted` does not mean Field
alignment passed; the JSON contains no measurement of the game's internal marker.

Key artifacts from this session:

- `field-absolute-20260928T172209285906Z.json`: first absolute midpoint.
- `field-relative-20260928T172239092204Z.json`: intervening relative movement.
- `field-absolute-20260928T172305073092Z.json`: same midpoint, different visible result.
- `field-relative-20260928T172743339019Z.json`: leftward movement and short reversal.
- `field-relative-20260928T172837963222Z.json`: further reversal to the center.

A rightward run at `20260928T172434198552Z` occurred after the chart ended. Its
unlocked clip rectangle makes it **invalid as Field evidence**. The default
locked-cursor guard was added after identifying this transition.

## Current relative-path spot check

On 2026-09-29, the Release Host at `31bbfc0` was checked again in the normal
Be There / MIN 3 chart after the game-managed sensitivity change. The client
remained `1280 x 720` at DPI 120. These probes use local TCP and real SendInput,
not phone touches or a dry-run sink. Each run begins a fresh Field gesture.

| Sequence | Approximate visible Field position |
| --- | --- |
| Fresh chart | X 512, centered |
| Relative `+0.05` | X 532 |
| Fresh gesture, relative `-0.05` | X 512 |
| Fresh gesture, `0`, then five `+0.01` events | X 532 |

The single displacement and its smaller increments reached similar positions,
and an equal reversal returned to the center. Positions are visual estimates
from the 1026-pixel-wide captures, not precision measurements. The zero event
and subsequent small steps share one observation, so this does not separately
prove physical lift/re-touch behavior. Fast swipes and phone hand feel remain open.

The three artifacts are `field-relative-20260928T182258559361Z.json`,
`field-relative-20260928T182320140281Z.json` and
`field-relative-20260928T182356710648Z.json` in `build/game-input-test/`.
All eight movement events received Field-owner ACK `4`; foreground and cursor
lock checks held throughout. The chart subsequently ended at its results screen.

## Required next evidence

Physical relative-input acceptance must compare slow/fast swipes, small movements,
direction reversals, lift/re-touch, simultaneous key holds, physical-mouse use,
device ownership handoff, focus changes and game sensitivity/window-size changes.
Automated transport and mapping tests establish the transmitted displacement,
not the final in-game hand feel or touch-to-photon latency.

Direct positioning now has a game-state reference and passing repeated-position
checks. It still needs physical finger/video alignment, simultaneous lane holds,
focus transitions and multi-device tests. Neither the recentered OS cursor nor
the visible Field edge should be used as the positioning reference.
