# Input protocol v2

Transport: TCP through ADB reverse, Android `127.0.0.1:27184` to the Windows
IPv4 loopback listener. Up to seven simultaneous controllers; no LAN listener or discovery port.
TCP_NODELAY is enabled at both ends. No authentication is provided by v2;
the trust boundary is the local PC and its authorized USB debugging devices.
Host and Android must be updated together; version 1 peers are rejected.

Each frame is exactly 32 bytes. All multibyte values are big-endian, including
the IEEE-754 binary32 bit representation. Never serialize a native C++ struct.

| Offset | Size | Field |
| --- | --- | --- |
| 0 | 4 | Magic bytes `49 46 54 31` (`IFT1`) |
| 4 | 1 | Version = 2 |
| 5 | 1 | Message type |
| 6 | 1 | Lane = 1..6 for lane messages; CONFIGURATION uses display mask; otherwise 0 |
| 7 | 1 | Status = 0 on requests; ACK 0 accepted/Field free, 1 inactive, 2 another device owns Field, 4 this device owns Field |
| 8 | 4 | Unsigned sequence, starting at 1 per connection |
| 12 | 4 | Float value, zero unless a Field message |
| 16 | 8 | Sender monotonic nanoseconds; opaque and echoed in ACK |
| 24 | 8 | Reserved, must be zero |

| Type | Name | Value / behavior |
| --- | --- | --- |
| 1 | HELLO | Must be first; release this session's state and establish v2 session |
| 2 | LANE_DOWN | Lane 1..6; default Shift/A/S/D/F/Space, actual keys follow IF configuration |
| 3 | LANE_UP | Matching lane release, duplicates harmless |
| 4 | FIELD_ABSOLUTE | Finite float in [0, 1] |
| 5 | FIELD_RELATIVE | Finite normalized width delta in [-1, 1] |
| 6 | RELEASE_ALL | Release this device's keys and Field ownership; clear its focus-loss input barrier |
| 7 | PING | Liveness request |
| 8 | FIELD_BEGIN | Claim Field or join its ownership queue; value zero |
| 9 | FIELD_END | Release Field, allowing the next waiting device to move it; value zero |
| 10 | ASSIGN_CONTROLS | Integer value 0..127; bits 0..5 enable lanes 1..6, bit 6 enables Field |
| 128 | ACK | Echo sequence and timestamp, lane/value zero |
| 129 | CONFIGURATION | Unsolicited Host-to-phone snapshot, described below; never acknowledged |

`FIELD_RELATIVE` is the default gameplay path. Android reports displacement as a
fraction of the configured Field touch span. Host converts it at a fixed 1280
mouse units per span, carrying fractional units within each gesture. It does not
scale by the selected window width, arrival interval or video resolution, and
adds no acceleration, smoothing or speed limit. Gameplay sensitivity belongs to
In Falsus. `FIELD_ABSOLUTE` remains available for experimental OS cursor mapping;
it does not establish an absolute position in the game's locked Field.

CONFIGURATION reuses the fixed frame with type-specific fields: lane contains the
display mask (0..127); status is 0 synced IF bindings, 1 defaults, or 2 unavailable
bindings/input paused. Sequence is a nonzero configuration generation. Value is
the integer connected-device count (1..7). Bytes 16..23 hold the six Unity Key
identifiers in the low 48 bits, lane 1 first; the high 16 bits are zero. Reserved
bytes remain zero. The client distinguishes this packet before matching pending
ACK sequence/timestamps. Other packet types retain opaque monotonic timestamps.

Each device selects its own display mask, including zero for view-only. Selection
changes release that device's input and require a new RELEASE_ALL barrier. Hidden
controls are ignored by the host as well as Android. Overlap is allowed and no
combined control count is imposed. A peer-count-only change does not clear holds.
IF binding changes release old keys across all sessions before applying new scans;
the new configuration and fresh barriers prevent stale holds from changing keys.

Windows counts key holders across devices (and handles duplicated IF key bindings).
Only the first DOWN and final UP are injected. Field belongs to the earliest active
touch, then the oldest remaining claimant. Movement from waiting devices is ignored.
For diagnostic clients, a movement implicitly begins Field ownership; Android sends
explicit BEGIN/END even for a stationary relative-mode finger.

Server ACKs each complete valid request only after applying it or deciding the
target is inactive. ACK statuses 2 and 4 are ready states, not focus loss.
Client RTT includes queueing, USB/TCP, processing and return;
it is not an end-to-end touch-to-photon measurement. Sequences wrap from
`0xffffffff` to 0. Unknown types, nonzero reserved fields, invalid lane/status,
nonfinite/out-of-range values, repeated HELLO or unexpected sequence close TCP.

Both implementations must handle partial reads and coalesced TCP packets.
No allocation is driven by a peer-provided length. A packet must complete within
500 ms per connection. Android sends PING at least every 100 ms when idle. Windows closes a
session after 500 ms without a valid packet; Android closes after 750 ms without
an ACK. Local EOF and explicit disconnect release only that device's contribution;
other controllers continue. Malformed peers and full reply queues are isolated.

When the target loses foreground status, the host releases keys and ACKs further
input with status 1 until RELEASE_ALL is received while the target is foreground.
The client clears touch state on inactive status and periodically sends the
barrier; a still-held finger must be lifted and pressed again. This prevents
stale holds or missed DOWNs when returning from another application.

Reconnect opens a new socket, uses HELLO sequence 1, clears pointers and queued
events, and requires fresh touches. No held-key state is replayed.
