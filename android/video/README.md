# Video boundary

`VideoClient` receives framed H.264 over the independent USB/TCP socket and owns
capped reconnect backoff. `SurfaceDecoder` chooses a hardware MediaCodec,
preferring advertised low-latency capability, and renders directly to Surface.
When the standard capability is absent, Android 12+ also checks the codec's
advertised vendor parameters for supported low-latency controls. Qualcomm OMX also
tries its known low-latency extension when discovery omits it. Rejected optional
configuration is retried without the control. Diagnostic text records the selected
option rather than assuming that the standard capability describes every driver.

Output uses Android's local monotonic time for Surface presentation. PC presentation
timestamps remain frame identifiers; they are not Android display deadlines.
Frames released for the same display refresh can be replaced by newer output.

The compressed queue contains two packets. Overflow discards dependent pictures
until the next IDR, then flushes and resends SPS/PPS. Codec input is bounded, stale
decoded output is not presented, and surface destruction closes the session.
`VideoStatistics` reports FPS, bitrate, queue/drops and local clock intervals.
Its bounded 240-presented-frame window separates receive-to-submit, decoding and
decode-to-present time, with mean and nearest-rank P95 values. These intervals stop
at the codec presentation callback and do not measure physical screen latency.
No video lock, callback or queue is shared with touch/control processing.

References: [Android Surface presentation timestamps](https://developer.android.com/reference/android/media/MediaCodec#releaseOutputBuffer(int,%20long)),
[vendor parameter discovery](https://developer.android.com/reference/android/media/MediaCodec#getSupportedVendorParameters()),
and [Moonlight's decoder compatibility controls](https://github.com/moonlight-stream/moonlight-android/blob/master/app/src/main/java/com/limelight/binding/video/MediaCodecHelper.java).
