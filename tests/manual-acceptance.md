# Physical-device and game acceptance

Status: physical gameplay checklist not executed. Android 14 device instrumentation
and real USB transport to a dry-run Windows host passed on 2026-09-29; see
`docs/STATUS.md`. These automated checks do not replace physical gameplay tests.

## Setup

- Build both artifacts, connect an authorized USB phone and run setup-adb.ps1.
- Select the running In Falsus window, open the app and focus the game.
- Check that the six labels match IF's saved bindings (defaults: Shift, A, S, D, F, Space).
- Select the plain US English keyboard layout for the game. Merely toggling an
  IME to English typing mode is insufficient when Shift itself is a lane key.
  Record the layout reported by Host before testing; restore the previous layout afterward.

## Touch and input

- Each lane triggers its matching key; release ends that key immediately.
- Two-, three- and six-key chords work; long holds survive other fingers tapping.
- Put two fingers on one lane, lift one, confirm the remaining hold continues.
- Slide a lane finger across all other lanes: ownership must stay on the origin.
- Slide a Field finger continuously while other fingers hold and tap lanes.
- Add a second Field finger: it must not steal control. Lift the owner: the
  second finger must not be promoted until it is lifted and pressed again.
- Rapidly tap the same lane and alternating lanes; confirm no missed UP/stuck key.
- Use Relative Field and tune sensitivity in In Falsus. Compare slow/fast swipes,
  tiny corrections, direction reversals and simultaneous six-key holds.
- In Relative mode, lift and re-touch at another position: Field must not jump. Test ownership
  handoff between devices without replaying the waiting device's movements.
- Compare the same swipe with different game window positions, sizes and DPI.
  Host uses a fixed conversion; record any game-dependent differences separately.
- Confirm fresh installs and upgraded preferences select Relative, and that
  language, theme, visible controls and calibrated coordinates survive upgrade.
- In Absolute mode on the supported build, touch-down must immediately reposition
  Field and sliding must follow the finger. Repeat left/center/right targets,
  lift and re-touch elsewhere, then change IF sensitivity and repeat. Game-state
  tests passed; physical touch/video alignment remains a separate check.
- Enable Button vibration in Touch settings: each new game-key press should give
  one brief system pulse. Holding, release, a second finger on the same held lane
  and Field sliding must not add pulses. Disable it and repeat; confirm the choice
  survives relaunch and respects the system haptic preference.
- In Aligned mode, verify four central keys and two side keys at both the enlarged
  upper button area and the judgment line. Fit bars must not trigger input.
- Adjust button height without moving the picture or judgment references. Field
  starts above the buttons; its Y coordinate does not move the PC pointer vertically.
- At 30% and 10% button heights, start Field touches just above central and slanted
  key edges. The visible Field outline must meet those edges without a dead strip;
  a touch just below the edge must still belong to the key.
- Verify immediate pressed fills, last-finger release and a distinct local Field marker.
- Use six-point judgment calibration with actual gameplay visible; verify rescaling
  keeps the touch geometry attached to the same chart positions.

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

## Interface and cooperative play

- Open and close settings repeatedly: app backgrounds must fill the screen and
  neither the settings nor the underlying game picture should briefly stretch.
- Check Chinese and English, consistent corners and large numbers above smaller
  synchronized key names. Long key names must fit without moving adjacent controls.
- Connect with Host absent, start Host, cancel a pending retry and disconnect.
  Feedback must be immediate; button positions must remain fixed through every state.
- The gameplay screen must show only the small settings entry with equal edge
  margins, avoiding the actual cutout. First tap shows an Android system Toast without
  opening settings; the second within two seconds opens it. A late second tap
  must start a new confirmation. USB controls belong only in the Connection page.
- Check that the return control stays at the top left. The confirmed opening tap
  must not activate it; an intentional return tap must close settings.
- Change settings and leave without a Save action: changes must persist. Defaults
  must first show a confirmation; Cancel or system Back must preserve all values,
  and confirming must retain language and theme while resetting controls/calibration.
- During repeated failed USB retries, the last result remains readable in the log.
  The status/action row stays in place; actual content changes resize smoothly.
- Try judgment calibration without video and invalid calibration points. Immediate
  feedback must be a native Android Toast in the selected language, without changing
  connection details or adding a page banner. A failed settings save retains edits
  for retry and its Toast remains visible across page changes.
- Connect two or more physical phones with setup-adb.ps1 -AllDevices. Each can
  select any subset of the six keys and Field, including no controls. Saving on one
  phone must not alter another phone's choices, and relaunch must preserve choices.
- Select one key, nonadjacent keys, and five keys. The visible group must be centered,
  preserve key shapes and labels, and trigger the original lane IDs at the new positions.
  Empty space must not send hidden-key input. Restoring six keys restores the chart layout.
- Hold a shared key on two phones and release or unplug one. The remaining phone's
  hold must continue. PC keyboard/mouse participation has no required coverage sum.
- Touch Field on two phones. The first owner retains control; release hands it to
  the waiting phone. A spectator or hidden control must never produce input.
- Change IF bindings while connected, then press fresh touches: the old keys must
  be released, all labels updated, and the new physical keys used. Unsupported
  binding states must pause input with an explanation.
- Seven simulated clients passed automated checks; seven physical phones still
  require a hardware acceptance run, including hub bandwidth and power behavior.

## Record

| Item | Evidence |
| --- | --- |
| Phone / simultaneous pointer capacity | Not tested |
| Android / Windows / In Falsus version | Android 14/API 34; In Falsus 1.0.4b |
| Six-key + Field simultaneous operation | Not tested |
| USB disconnect release timing | Not tested |
| Real SendInput accepted by game | USB Shift+Space starts tutorial; full six-key gameplay remains open |
| Absolute Field touch-to-marker alignment | Replacement passed game-state repeated targets and 120-event/s sweep; physical phone/video alignment remains open; see [investigation](../docs/FIELD-MAPPING.md) |
| Measured control RTT (method/sample count) | Four end-of-run USB software-input samples during video: 6.50/3.19/3.53/2.84 ms; physical finger-to-game latency remains unmeasured |

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
- Test 1080p60 and bitrate/FPS alternatives during actual charts. Short 720p60 and
  1080p60 pattern runs passed on the connected Android 14 phone on 2026-09-29.
- Host accepts experimental rates up to 120 FPS, but this phone's latest 720p120
  probe reported only 93.27 presentation callbacks/s after fixing timing-record
  eviction/accounting, and failed its throughput
  gate. Keep the 60 FPS baseline until both presentation and latency improve;
  display refresh rate and decoder format support are not throughput results.
- Test unsupported codecs and GPU/device-loss failures: video errors should be
  explicit and must not block input cleanup.
- Use high-speed external recording for physical touch-to-photon latency. Do not
  infer it by adding timestamps from unsynchronized PC and phone clocks.
- For Late-heavy play, compare the same chart segment using phone controls while
  watching the PC and then the phone. Keep game offsets and sound setup unchanged
  during comparison; separately record muted visual play and PC-audio play.
- Distinguish measured pipeline delay from in-game calibration. The inspected
  1.0.4b audio offset changes the clock shared by input and track rendering; it
  cannot independently compensate delayed video during muted visual play. Follow
  [the timing comparison procedure](../docs/TIMING-CALIBRATION.md#practical-verification)
  and verify direction again for other binaries before suggesting a value.
  Retest after any video optimization; a constant offset does not correct jitter.
