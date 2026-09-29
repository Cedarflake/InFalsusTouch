# Settings and calibration

Android preferences remain on the phone. Windows Field and video preferences
remain on the PC; neither side uploads configuration to a service.

## Phone

On the tested Xiaomi phone, **three-finger swipe to screenshot** cancels all held
game controls when a third finger slides. The user confirmed that turning off
that system shortcut restores two held ground keys plus Field sliding. A screenshot
does not have to appear for the gesture to intercept touches. Keeping this shortcut
enabled still causes cancellation on the tested phone; the controller does not change
the user's system preference.

Tap the small **Settings** entry twice within two seconds. The first tap uses an
Android system Toast for “Tap again to open settings”; expiration resets the guard.
The return control stays at the top left; **Defaults** is at the top right.
Settings apply and save automatically, with no Save/Cancel footer. Slider changes
are coalesced while dragging and flushed on release, exit or backgrounding.
Writes are serialized so incoming status updates cannot overwrite newer edits.
A failed write uses an Android system Toast and pending edits remain available for retry;
session settings remain usable. Invalid stored values fall back to defaults
with a localized system Toast. Immediate feedback for unavailable video, invalid
settings, invalid calibration and failed actions also uses the same native Toast
path. These messages do not add banners or alter the connection log. A message
generated while backgrounded waits until the Activity resumes. Defaults asks for confirmation before resetting phone
controls and calibration while retaining the chosen language and theme. Canceling
or dismissing the dialog leaves settings unchanged.
The sidebar order is **Connection, Touch, Controls, Picture, Other**.
**Other** holds English/Chinese and Light/Dark/System appearance; changes take effect immediately.
The app fills the physical screen. Settings transitions keep a fixed Flutter surface;
only controls and text avoid the camera cutout. USB actions and connection details
live in **Connection**, which opens first when disconnected. The latest transport
log is inside the USB card; automatic discovery remains in a separate section. The
status and action share a row, with connected devices in a small header badge.
The action keeps fixed bounds and centered text. The inset log retains the latest
connection outcome during retries; progress appears in the status row. Real content
changes resize the card smoothly, honoring reduced-motion settings. Empty metrics
have no reserved height; RTT remains in performance statistics. Gameplay shows only
a 48 dp settings entry with equal 8 dp edge margins;
it switches corners only if the actual cutout overlaps. UI buttons/chips use full
pill shapes, the settings entry is circular, and cards have larger 32 dp corners.
Gameplay lane shapes are unchanged. In Aligned mode, a partial key selection keeps
each button at its calibrated video track position, leaving hidden lanes empty.
Fixed and Reserved modes pack selected keys in lane order and center the group.
Drawing and hit testing share the same regions; key numbers and bindings retain
their original lane identity. Showing all six keys keeps the original chart layout.
Field and video placement remain independent. Settings use the full screen
width; only controls intersecting an actual cutout receive local padding.

| Setting | Default / bounds |
| --- | --- |
| Language | System initially; English or 中文 can be chosen and persisted |
| Theme | Dark initially; Light, Dark or System can be chosen and persisted |
| Visible controls | All six lanes and Field; any subset including none is valid and saved per phone |
| Field mode | Relative; Absolute remains experimental |
| Layout | Aligned Field + Floor; fixed Overlay and Reserved are also available |
| Video scaling | Fit: complete, centered, undistorted image; Stretch changes proportions; Crop hides edges |
| Prefer 120 Hz display | On; requests 120 Hz for the controller window, subject to system policy |
| Button touch height | 40%; 10–50%; extends aligned keys upward without moving judgment lines |
| Field height | 65%; 10–100%, clipped above the lane region |
| Phone Field left / right | 0% / 100%; left must be smaller than right |
| Lane opacity | 45%; 0–100% |
| Lane gap | 2 dp; 0–20 dp; hit regions remain contiguous |
| Lane brightness | 100%; 10–100% |
| Labels | On |
| Button vibration | Off; optional native feedback on game-key press |
| Video statistics / Field outline | Off |
| Find USB Host automatically | Off; opt in for foreground retry |
| Settings entry | Two taps within two seconds; no expanded gameplay toolbar |

Field uses horizontal relative movement by default. Adjust mouse sensitivity in
In Falsus. Touch-down establishes a new origin without moving Field, so lifting
and touching elsewhere does not reposition it. On upgrade, preferences written
before this change switch to Relative without resetting language, theme, visible
keys or calibration. Explicitly selecting experimental Absolute afterward is
persisted normally. Absolute now targets the game Field directly in the supported
In Falsus 1.0.4b build: touch-down repositions it, movement follows the finger and
lift completes the final position. Host reads effective sensitivity on every
correction, so no particular game sensitivity is required. Other game builds need
Relative until their state layout is verified. Physical picture-to-finger alignment
still depends on the phone's judgment-line calibration.

**Button vibration**, under Touch, uses Android's native virtual-key feedback on
the first press of each lane. Held keys, additional fingers on an already-held lane,
release and Field sliding do not generate extra pulses. It saves automatically and
does not wait for Host acknowledgements. It follows the phone's system haptic setting;
see [Android haptic feedback](https://developer.android.com/develop/ui/views/haptics/haptic-feedback).

Fit is the recommended reading mode. A 16:9 game occupies 1920 x 1080 pixels on
a 2400 x 1080 phone, with 240-pixel side bars. App UI insets do not resize this
picture. Video, lane controls and Field calibration share physical screen coordinates.
The default Aligned layout centers video on the screen; Reserved centers it in
the smaller area above the lanes. Crop would hide 135 pixels at each of the top
and bottom edges when filling this phone, potentially obscuring notes or the
judgment line. It is available only as an explicit choice.

Display refresh and video FPS are independent. The high-refresh preference
requests 120 Hz for touch feedback; the video Surface still reports the source
FPS. Gameplay statistics use one right-aligned, translucent line for presented
video FPS, actual panel Hz and input RTT. The Picture settings page retains the
detailed statistics in a separate card: a responsive grid aligns metric labels,
tabular numbers and units, with queue depth and session drops below. The card header
shows the PC source resolution and configured video FPS from the received stream.
Turning the preference off returns the
window to system selection, not a forced 60 Hz mode. Device policy and power
saving may override the request; no global display settings are changed.

Aligned layout follows the upper Field line, the four central Floor lanes and
the two slanted side judgment lines. Coordinates are stored relative to the
encoded picture, so Fit/Stretch/Crop share the same transform for video and touch.
Invisible crop regions and letterbox bars cannot acquire a pointer. Buttons grow
upward to the selected height; Field accepts horizontal movement above the buttons.
Its touch boundary and optional outline meet the buttons' actual upper edges,
including the side slopes, so lowering the buttons does not leave an unresponsive
strip below Field. Judgment-line calibration does not limit this touch height.
The default 40% button height is independent of the lower judgment-line height.
Pressed fills and judgment highlights are rendered locally and stay active until
the last finger on that lane lifts. The Field marker shows the local touch position,
not a confirmation of the PC game's cursor position.
Each lane places a large bold number above a smaller synchronized IF key name.
Both lines are centered, with consistent type sizes and fitting for long key names.

**Controls / 按键显示** uses seven independent chips with current IF key names.
A top information card combines the seven-device cooperative play explanation
with key synchronization and IME guidance. The selection card follows without additional help text.
Hidden lanes cannot acquire a touch. Selected keys keep their track positions in
Aligned mode and center as a group in Fixed and Reserved modes; widths are preserved.
All / Keys only / Field only / View only presets affect only this device. In Field-only
mode the gesture region extends farther down the picture. Other phones can overlap
these choices, and unassigned operations may be handled by the PC keyboard/mouse.
There is no forced sum across devices. The connection count includes view-only phones.
If another phone currently owns Field, local feedback is amber until ownership passes.

**Align game judgment lines** records six taps: Field left/right, central Floor
left/right, then the outer left/right side-line endpoints. Points use video
coordinates and are rejected when reversed or overlapping. Calibrate with visible
gameplay lines. The preset was observed in the installed game's 16:9 tutorial;
it is not automatic chart recognition. Existing saved layouts remain selected.

The fixed-layout Field calibration screen covers the same usable display area as the
controller; its toolbar separately avoids the cutout. Tap the comfortable left
edge, then the right edge, within the Field region. The minimum calibrated span
is 5% of the controller width. Save commits the range; Cancel leaves it unchanged.
Opening calibration first saves any pending settings. Editing an
unrelated setting preserves the full precision of calibrated endpoints.

All input is released and disabled during settings/calibration. After closing,
fresh pointer-down events are required; old held fingers are never replayed.

Automatic discovery uses only the fixed localhost control endpoint provided by
ADB reverse. Start Host and run `scripts/setup-adb.ps1` first. Failed connections
retry at 0.5, 1, 2, then 4 seconds while the app is foregrounded. Manual Disconnect
stops retries. Backgrounding closes both input and video sessions. Reopening
the app connects again if automatic discovery is enabled. Unplugging USB or
restarting ADB may require running the PC setup script again.

## PC

Host loads `%LOCALAPPDATA%\InFalsusTouch\host.ini` if it exists. `--profile PATH`
selects another file. Explicit CLI settings override loaded values regardless
of argument order. `--save-profile` writes Field/video settings and exits;
it does not start capture or input injection. `--no-profile` ignores defaults.
Dry-run tests ignore the default file, but can load an explicitly named profile.

| Option | Default / bounds |
| --- | --- |
| `--field-left`, `--field-right` | 0.05 / 0.95; normalized selected client, left < right |
| `--field-y` | 0.5; normalized fixed client Y |
| `--resolution` | `720p`; also `1080p` |
| `--fps` | 60; 24–120 |
| `--bitrate` | 8000000; 500000–40000000 bits/second |

For a 120 Hz phone, `--resolution 720p --fps 120` requests an experimental 120 FPS stream.
Use the updated Host and APK together. The phone display preference is independent
of this setting; actual capture, encoding and presentation must keep up with the
requested rate. The default remains 60 FPS. Compare delivered FPS and latency
before saving a higher rate, especially at 1080p or with multiple viewers. The
connected phone has not passed the 120 FPS presentation gate; see the measured
results in [STATUS.md](STATUS.md#high-frame-rate-probe-2026-09-29).

Relative Field preserves horizontal View-pixel displacement as mouse movement
units. Calibration no longer changes the amount of movement, and a swipe can
continue beyond the calibrated Field edges. This conversion does not depend on
game window size, video resolution or packet arrival times. Fractional units carry into subsequent movements within
the gesture. Host adds no sensitivity multiplier, acceleration, smoothing or speed
cap; adjust gameplay sensitivity in In Falsus. The Windows input path remains
SendInput, and the app does not change system mouse settings.

`--calibrate` controls OS cursor mapping for menus and diagnostic targets. It does
not change direct gameplay positioning. It selects a game window and
records the PC cursor at three prompts:
left endpoint, right endpoint, and fixed height. Keep the console focused and
the game visible beside it, move the mouse without clicking, then press Enter.
Type `q` to cancel. Points outside the game client, reversed endpoints, or a
window that moves/resizes/closes invalidate the calibration without saving.
The result uses physical client coordinates and does not affect relative input.
Restart Host after calibration to use the saved values.

The earlier OS-absolute approach failed normal-chart probes. The replacement reads
game Field state through a read-only process handle, checks the supported binary's
SHA-256, and sends ordinary relative mouse input. It never writes game memory or
changes game sensitivity. Repeated targets and a 120-events/second sweep passed
game-state checks; complete physical phone-to-picture acceptance remains open.
Phone judgment alignment and PC cursor calibration are separate. Do not infer
in-game alignment from GetCursorPos alone.
See [the Field investigation](FIELD-MAPPING.md) for the current evidence.

Profiles are a bounded UTF-8/ASCII `key=value` format, saved with `version=2`.
Version 1 files still load: valid legacy sensitivity/acceleration/smoothing/speed
values are ignored with a console notice, while calibration and video settings
are preserved. Saving rewrites them as version 2 without those retired values.
The old CLI flags are rejected with guidance to use In Falsus's sensitivity setting.
Unknown, duplicate, malformed or unsupported values are errors. Save flushes a temporary
file then replaces the destination; a failed validation leaves the existing
file unchanged. Window handles, diagnostic flags, port overrides and trace paths
are session options and are not persisted. Copying the default profile to a
named path is sufficient for keeping multiple game/window calibrations.
