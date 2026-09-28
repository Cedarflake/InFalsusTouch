# Video protocol v1

The host listens on **127.0.0.1:27183**. ADB reverse carries it over the authorized
USB devices. This socket never carries input. Up to seven viewers share one capture
and encoder; each has an independent configuration, sequence and send queue. New
viewers wait for the next IDR without restarting existing streams.

Each packet is a fixed 64-byte, big-endian header followed by exactly the declared
payload. No native structs are serialized. Validate the complete header before
allocating a payload. The maximum payload is 4 MiB; configuration is limited to
64 KiB and error text to 4 KiB.

| Offset | Bytes | Field |
| --- | --- | --- |
| 0 | 4 | ASCII `IFV1` |
| 4 | 1 | Version `1` |
| 5 | 1 | Type: CONFIG=1, FRAME=2, ERROR=3 |
| 6 | 2 | Flags: bit 0 is IDR; all other bits zero |
| 8 | 4 | Payload length, nonzero |
| 12 | 4 | Sequence: CONFIG=0, then FRAME=1,2,..., wrapping at uint32 |
| 16 | 8 | captureTimestamp, PC nanoseconds |
| 24 | 8 | encodeTimestamp, PC nanoseconds |
| 32 | 8 | sendTimestamp, PC nanoseconds |
| 40 | 8 | Presentation timestamp = captureTimestamp / 1000 |
| 48 | 2 | Width, even, 128..1920 |
| 50 | 2 | Height, even, 128..1080 |
| 52 | 2 | Target FPS, 24..60 |
| 54 | 2 | Reserved, zero |
| 56 | 4 | Requested bitrate, 500000..40000000 bits/s |
| 60 | 4 | Reserved, zero |

CONFIG contains Annex B SPS and PPS NAL units. Its capture/encode/PTS fields are
zero and its IDR flag is clear. FRAME contains one Annex B access unit, including
all slices for that picture; its IDR flag must agree with NAL type 5. Timestamps
must satisfy capture <= encode <= send, and capture must increase between frames.
ERROR contains UTF-8 diagnostic text and may appear before or after CONFIG. A
format change requires reconnect, with a new configuration and IDR. An ERROR
sequence is informational; the receiver closes the session after reporting it.

The host uses H.264 Baseline, which forbids B slices, and requests a half-second
GOP. It verifies output timestamps against submitted samples and rejects frame
reordering. Driver-specific encoder controls are checked; an unsupported optional
setting is reported instead of silently being described as active.

## Clock definitions

All three PC timestamps use QueryPerformanceCounter converted to nanoseconds:

- captureTimestamp: the WGC callback successfully retrieves a frame. This is an
  application availability boundary, not game rendering or compositor latency.
- encodeTimestamp: the encoder produces that frame's complete output sample.
- sendTimestamp: the sender begins writing the header; it is not network delivery.

On Android, System.nanoTime records full-packet receive, codec submission and
decoded-output availability. MediaCodec's frame-rendered callback supplies a
local presentation timestamp. These are retained in `FrameTiming` for debugging.
Never subtract a PC timestamp from an Android timestamp. The UI reports local
stage intervals, not glass-to-glass latency; callback presentation is not a
measurement of physical display light.

`presentationUs` is used to match codec output to its original packet. Rendering
supplies a separate Android-local monotonic timestamp to the Surface; the PC clock
must not schedule the phone's display. The Android diagnostic snapshot also carries
mean and P95 stage intervals over the last 240 presented frames. No PC-to-phone
subtraction or one-way network-latency inference is made from that window.

## Bounds and recovery

- WGC has two surfaces and a latest-frame slot. Replaced captures are closed.
- At most three samples are in the encoder. NV12 textures stay referenced by their
  own MF samples; no surface is overwritten while the encoder owns it.
- Each viewer uses a 32 KiB socket buffer, at most three queued packets (including
  initial configuration), a 256 KiB send budget per pump and a 100 ms packet deadline.
  Immutable encoded payloads are shared. A slow peer loses only its video session;
  other viewers and the independent input worker remain running.
- After a bounded encoder warm-up, a PC frame age above 250 ms restarts the stream.
- Android validates configuration within 5 seconds and partial packets within
  500 ms. An unchanged source may idle between complete packets.
- Android holds at most two compressed packets plus at most two submitted codec
  samples and one current packet. On queue overflow it clears pending packets,
  discards dependent pictures until the next IDR, flushes and resubmits SPS/PPS.
- Already decoded pictures older than 120 ms on the phone are not presented.
- Video reconnect uses capped backoff and starts from fresh CONFIG/IDR. It does
  not reconnect or replay input. Surface destruction closes the video socket.

Static-window update rate, source frame rate, GPU load, USB behavior and decoder
support affect delivered FPS. A configured 60 FPS is a ceiling, not proof of 60
frames presented every second.
