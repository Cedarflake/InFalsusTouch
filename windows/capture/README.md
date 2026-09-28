# Capture boundary

`WindowCapture` uses a free-threaded WGC pool and a latest-frame slot. It checks
window identity, closes replaced frames, recreates on size changes and crops to
the selected window's physical client area. Supported Windows builds allow the
host to own frame pacing rather than inheriting WGC's default minimum interval.

`FrameConverter` scales with aspect preservation and converts BGRA to NV12 on the
GPU. Device errors terminate only the video session and are reported explicitly.
Capture timestamps mark frame availability to this application.
