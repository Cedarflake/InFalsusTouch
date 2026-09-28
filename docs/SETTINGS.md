# Settings and calibration

Android preferences remain on the phone. Windows Field and video preferences
remain on the PC; neither side uploads configuration to a service.

## Phone

Open **Settings** from the toolbar, or **Menu** after it hides. Save applies the
changes and commits them on a background writer. A failed write is reported;
session settings remain usable. Invalid stored values fall back to defaults
with a visible notification. Defaults resets the entire phone configuration.

| Setting | Default / bounds |
| --- | --- |
| Field mode | Absolute; Relative is available |
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
| Hide toolbar while connected | On; four seconds, with a Menu button |

Fit is the recommended reading mode. A 16:9 game occupies 1920 x 1080 pixels on
a 2400 x 1080 phone, with 240-pixel side bars. Opposing cutout/system-bar insets
are made symmetric so a camera cutout cannot move the picture off center.
Video, lane controls and Field calibration share the same safe content area.
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

**Align game judgment lines** records six taps: Field left/right, central Floor
left/right, then the outer left/right side-line endpoints. Points use video
coordinates and are rejected when reversed or overlapping. Calibrate with visible
gameplay lines. The preset was observed in the installed game's 16:9 tutorial;
it is not automatic chart recognition. Existing saved layouts remain selected.

The fixed-layout Field calibration screen covers the same usable display area as the
controller, including the same system/cutout insets. Tap the comfortable left
edge, then the right edge, within the Field region. The minimum calibrated span
is 5% of the controller width. Save commits the range; Cancel leaves it unchanged.
Opening calibration first saves any pending settings in the dialog. Editing an
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
| `--sensitivity` | 1; >0 and <=20 |
| `--acceleration` | 0; 0–10 |
| `--smoothing` | 0; >=0 and <1 |
| `--max-speed` | 12000; >0 and <=100000 pixels/second |
| `--resolution` | `720p`; also `1080p` |
| `--fps` | 60; 24–60 |
| `--bitrate` | 8000000; 500000–40000000 bits/second |

`--calibrate` selects a game window and records the PC cursor at three prompts:
left endpoint, right endpoint, and fixed height. Keep the console focused and
the game visible beside it, move the mouse without clicking, then press Enter.
Type `q` to cancel. Points outside the game client, reversed endpoints, or a
window that moves/resizes/closes invalidate the calibration without saving.
The result uses physical client coordinates and retains relative-mode settings.
Restart Host after calibration to use the saved values.

Actual In Falsus 1.0.4b tutorial probes show cursor locking/recentering. The default
absolute client mapping moves the in-game Field cursor over only part of its range.
Phone judgment alignment and PC cursor calibration are separate; absolute gameplay
alignment remains unresolved. Relative input moves the cursor, with travel depending
on game/Host sensitivity. Do not infer in-game alignment from GetCursorPos alone.

Profiles are a bounded UTF-8/ASCII `key=value` format with `version=1`. Unknown,
duplicate, malformed or unsupported values are errors. Save flushes a temporary
file then replaces the destination; a failed validation leaves the existing
file unchanged. Window handles, diagnostic flags, port overrides and trace paths
are session options and are not persisted. Copying the default profile to a
named path is sufficient for keeping multiple game/window calibrations.
