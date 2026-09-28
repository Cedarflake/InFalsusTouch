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
| Layout | Overlay; Reserved keeps video above the lanes |
| Video scaling | Fit; Fill stretches, Crop hides overflowing edges |
| Lane height | 28%; 10–50% of the controller height |
| Field height | 65%; 10–100%, clipped above the lane region |
| Phone Field left / right | 0% / 100%; left must be smaller than right |
| Lane opacity | 45%; 0–100% |
| Lane gap | 2 dp; 0–20 dp; hit regions remain contiguous |
| Lane brightness | 100%; 10–100% |
| Labels | On |
| Video statistics / Field outline | Off |
| Find USB Host automatically | Off; opt in for foreground retry |
| Hide toolbar while connected | On; four seconds, with a Menu button |

The Field calibration screen covers the same usable display area as the
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

Profiles are a bounded UTF-8/ASCII `key=value` format with `version=1`. Unknown,
duplicate, malformed or unsupported values are errors. Save flushes a temporary
file then replaces the destination; a failed validation leaves the existing
file unchanged. Window handles, diagnostic flags, port overrides and trace paths
are session options and are not persisted. Copying the default profile to a
named path is sufficient for keeping multiple game/window calibrations.
