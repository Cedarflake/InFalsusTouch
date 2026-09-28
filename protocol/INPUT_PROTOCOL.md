# Input protocol v1

Transport: TCP through ADB reverse, Android `127.0.0.1:27184` to the Windows
IPv4 loopback listener. One client at a time; no LAN listener or discovery port.
TCP_NODELAY is enabled at both ends. No authentication is provided by v1;
the trust boundary is the local PC and its authorized USB debugging devices.

Each frame is exactly 32 bytes. All multibyte values are big-endian, including
the IEEE-754 binary32 bit representation. Never serialize a native C++ struct.

| Offset | Size | Field |
| --- | --- | --- |
| 0 | 4 | Magic bytes `49 46 54 31` (`IFT1`) |
| 4 | 1 | Version = 1 |
| 5 | 1 | Message type |
| 6 | 1 | Lane = 1..6 for lane messages, otherwise 0 |
| 7 | 1 | Status = 0 on requests; ACK 0 accepted, 1 target inactive |
| 8 | 4 | Unsigned sequence, starting at 1 per connection |
| 12 | 4 | Float value, zero unless a Field message |
| 16 | 8 | Sender monotonic nanoseconds; opaque and echoed in ACK |
| 24 | 8 | Reserved, must be zero |

| Type | Name | Value / behavior |
| --- | --- | --- |
| 1 | HELLO | Must be first; release old state, establish v1 session |
| 2 | LANE_DOWN | Lane 1 Shift, 2 A, 3 S, 4 D, 5 F, 6 Space |
| 3 | LANE_UP | Matching lane release, duplicates harmless |
| 4 | FIELD_ABSOLUTE | Finite float in [0, 1] |
| 5 | FIELD_RELATIVE | Finite normalized width delta in [-1, 1] |
| 6 | RELEASE_ALL | Release every key; clear focus-loss input barrier |
| 7 | PING | Liveness request |
| 128 | ACK | Echo sequence and timestamp, lane/value zero |

Server ACKs each complete valid request only after applying it or deciding the
target is inactive. Client RTT includes queueing, USB/TCP, processing and return;
it is not an end-to-end touch-to-photon measurement. Sequences wrap from
`0xffffffff` to 0. Unknown types, nonzero reserved fields, invalid lane/status,
nonfinite/out-of-range values, repeated HELLO or unexpected sequence close TCP.

Both implementations must handle partial reads and coalesced TCP packets.
No allocation is driven by a peer-provided length. A packet must complete within
500 ms. Android sends PING at least every 100 ms when idle. Windows closes a
session after 500 ms without a valid packet; Android closes after 750 ms without
an ACK. Local EOF and explicit disconnect are handled immediately when observed.

When the target loses foreground status, the host releases keys and ACKs further
input with status 1 until RELEASE_ALL is received while the target is foreground.
The client clears touch state on inactive status and periodically sends the
barrier; a still-held finger must be lifted and pressed again. This prevents
stale holds or missed DOWNs when returning from another application.

Reconnect opens a new socket, uses HELLO sequence 1, clears pointers and queued
events, and requires fresh touches. No held-key state is replayed.
