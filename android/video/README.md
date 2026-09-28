# Video boundary

`VideoClient` receives framed H.264 over the independent USB/TCP socket and owns
capped reconnect backoff. `SurfaceDecoder` chooses a hardware MediaCodec,
preferring advertised low-latency capability, and renders directly to Surface.

The compressed queue contains two packets. Overflow discards dependent pictures
until the next IDR, then flushes and resends SPS/PPS. Codec input is bounded, stale
decoded output is not presented, and surface destruction closes the session.
`VideoStatistics` reports FPS, bitrate, queue/drops and local clock intervals.
No video lock, callback or queue is shared with touch/control processing.
