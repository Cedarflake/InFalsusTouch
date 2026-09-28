# Settings and calibration

Android preferences remain on the phone. Windows Field and video preferences
remain on the PC; neither side uploads configuration to a service.

## Phone

Tap the small **Settings** entry twice within two seconds. The first tap uses an
Android system Toast for “Tap again to open settings”; expiration resets the guard.
The return control stays at the top left; **Defaults** is at the top right.
Settings apply and save automatically, with no Save/Cancel footer. Slider changes
are coalesced while dragging and flushed on release, exit or backgrounding.
Writes are serialized so incoming status updates cannot overwrite newer edits.
A failed write is reported and pending edits remain available for retry;
session settings remain usable. Invalid stored values fall back to defaults
with a visible notification. Defaults asks for confirmation before resetting phone
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
Gameplay lane shapes are unchanged. A partial key selection packs in original lane
order and centers horizontally, preserving each key's width and height. Drawing
and hit testing share those translated regions; key numbers and bindings retain
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
| Video statistics / Field outline | Off |
| Find USB Host automatically | Off; opt in for foreground retry |
| Settings entry | Two taps within two seconds; no expanded gameplay toolbar |

Field uses horizontal relative movement by default. Adjust mouse sensitivity in
In Falsus. Touch-down establishes a new origin without moving Field, so lifting
and touching elsewhere does not reposition it. On upgrade, preferences written
before this change switch to Relative without resetting language, theme, visible
keys or calibration. Explicitly selecting experimental Absolute afterward is
persisted normally. The experimental mode positions the OS cursor; it has not
passed game Field alignment tests.

Fit is the recommended reading mode. A 16:9 game occupies 1920 x 1080 pixels on
a 2400 x 1080 phone, with 240-pixel side bars. App UI insets do not resize this
picture. Video, lane controls and Field calibration share physical screen coordinates.
The default Aligned layout centers video on the screen; Reserved centers it in
the smaller area above the lanes. Crop would hide 135 pixels at each of the top
and bottom edges when filling this phone, potentially obscuring notes or the
judgment line. It is available only as an explicit choice.

Display refresh and video FPS are independent. The high-refresh preference
requests 120 Hz for touch feedback; the video Surface still reports the source
FPS. The statistics overlay reports the actual panel mode separately from
received/decoded/presented video frames. Turning the preference off returns the
window to system selection, not a forced 60 Hz mode. Device policy and power
saving may override the request; no global display settings are changed.

Aligned layout follows the upper Field line, the four central Floor lanes and
the two slanted side judgment lines. Coordinates are stored relative to the
encoded picture, so Fit/Stretch/Crop share the same transform for video and touch.
Invisible crop regions and letterbox bars cannot acquire a pointer. Buttons grow
upward to the selected height; Field accepts horizontal movement above the buttons.
The default 40% button height is independent of the lower judgment-line height.
Pressed fills and judgment highlights are rendered locally and stay active until
the last finger on that lane lifts. The Field marker shows the local touch position,
not a confirmation of the PC game's cursor position.
Each lane places a large bold number above a smaller synchronized IF key name.
Both lines are centered, with consistent type sizes and fitting for long key names.

**Controls / 按键显示** uses seven independent chips with current IF key names.
A top information card combines the seven-device cooperative play explanation
with key synchronization and IME guidance. The selection card follows without additional help text.
Hidden lanes cannot acquire a touch; selected lane subsets keep their widths and center as a group.
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
| `--fps` | 60; 24–60 |
| `--bitrate` | 8000000; 500000–40000000 bits/second |

Relative Field converts a full touch-area width to a fixed 1280 mouse movement
units. This unit conversion does not depend on game window size, video resolution
or packet arrival times. Fractional units carry into subsequent movements within
the gesture. Host adds no sensitivity multiplier, acceleration, smoothing or speed
cap; adjust gameplay sensitivity in In Falsus. The Windows input path remains
SendInput, and the app does not change system mouse settings.

`--calibrate` is for experimental absolute mapping. It selects a game window and
records the PC cursor at three prompts:
left endpoint, right endpoint, and fixed height. Keep the console focused and
the game visible beside it, move the mouse without clicking, then press Enter.
Type `q` to cancel. Points outside the game client, reversed endpoints, or a
window that moves/resizes/closes invalidate the calibration without saving.
The result uses physical client coordinates and does not affect relative input.
Restart Host after calibration to use the saved values.

Actual In Falsus 1.0.4b normal-chart probes show cursor locking and history-dependent
Field positions for identical absolute requests. Adjusting endpoints alone is insufficient.
Phone judgment alignment and PC cursor calibration are separate; absolute gameplay
alignment remains unresolved. Relative input is the chosen gameplay path, with
sensitivity controlled in the game. Do not infer in-game alignment from GetCursorPos alone.
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
