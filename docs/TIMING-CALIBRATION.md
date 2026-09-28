# Timing calibration and video delay

For phone-screen play, reduce streaming delay before calibrating against music.
IF's audio offset is not an independent adjustment of input judgment relative to
the displayed notes. In the inspected build it enters the gameplay clock used by
both input processing and track rendering. It cannot be treated as a direct
compensation for delayed phone video during muted visual play.

## Evidence scope

Read-only inspection on 2026-09-29 used the installed In Falsus 1.0.4b
`GameAssembly.dll`, existing IL2CPP method metadata, and narrow native disassembly.
The DLL SHA-256 was:

```text
AB1D8FA7739078510FAB5F8580095C90EA0F5E9236AC9B918E934CB9AE1B7D9C
```

The settings type and offset key were also identified through bounded reads of
the running game's memory. No code was injected, no input was sent, and no game
files, memory or settings were written. The saved `user_audio_offset_ms` remained
`0`. Addresses below are RVAs for this exact binary, not stable game APIs.

This establishes the clock's offset direction from code. It is not a physical
calibration, a measured improvement in judgments, or a claim about every special
encounter or future game version.

## Offset path

| Location | Observed behavior |
| --- | --- |
| Settings type slot `0x31A7B08` | Runtime type `_k._kG`; its static field at `+0xD0` identifies `user_audio_offset_ms` |
| Quick-settings callbacks `0x6C4970`, `0x6C4A20`, `0x6C56B0` | Read offset; increment/decrement by 1 ms; clamp to -1000..1000 ms |
| Gameplay controller `_E._VD._mz`, `0x5F24A0` | Reads the offset, negates it and stores it at instance `+0xC0` |
| Controller clock helper `_wz`, `0x5F4690` | Active playback branches add the supplied offset in seconds to the player's position; special-scene code may then override the result |
| Key input `_Pz`, `0x5F0730` | Converts the stored offset using `0.001`, calls the clock helper and stores the result at `+0xD0` before processing input |
| Gameplay update `_uz`, `0x5F3D50` | Updates the same `+0xD0` clock, used by gameplay processing |
| Track `Update`, `0x669690` | Reads controller `+0xD0` at `0x669CEC`; track time and note queries use this clock |

For the active playback branch before a special-scene override, the relevant
relationship is:

```text
gameplay time in seconds = player position in seconds - audio offset in ms / 1000
```

Increasing the saved offset therefore makes gameplay time earlier at a fixed
player position. For a press held at the same time relative to the music, this
moves its timing toward Early. The track also reaches a given chart time later;
the setting does not move only judgment while keeping the displayed notes fixed.

As an idealized constant-delay example, suppose the player position follows real
time, the note time is `N`, the offset is `O`, and the video/input delays are `V`
and `I`, all in milliseconds. The note reaches the PC judgment line at `N + O`.
A press following that displayed moment reaches the game at `N + O + V + I`.
The adjusted gameplay time at that press is then `N + V + I`: the offset cancels.
This explains why audio calibration alone cannot remove streaming delay for pure
visual timing. Real frame pacing, player anticipation and special chart behavior
make an actual play comparison necessary.

## Practical verification

1. Keep the game offset, chart segment and video profile fixed. Use phone controls
   while alternating between the PC and phone display. Record Early/Late results
   separately for muted play and play with PC audio.
2. If the phone display adds Late judgments, prioritize capture, decode and
   presentation latency. Increasing an audio offset or slowing the scroll speed
   does not remove that pipeline delay.
3. Once the video setup is stable, calibrate music-based timing with the same
   audio output used for play. For this inspected clock path, a small positive
   offset is the direction to test for consistent Late presses relative to music;
   compare repeated results before retaining it. Do not apply that direction as
   a guaranteed fix for muted phone-screen play.
4. Recheck after changing the video profile, display or audio output. A constant
   offset cannot correct variable delay. Never use input RTT or receive-to-present
   time alone as the total compensation value.

[STATUS.md](STATUS.md#video-measurements) contains the measured pipeline results.
The earlier game-screen run measured 26.74 ms mean / 31.08 ms P95 from phone
receive to presentation callback timestamp. It used song selection, not a busy
chart, and predates the callback-accounting correction. The later corrected
720p60 pattern run measured 31.94 ms mean / 37.77 ms P95. Neither includes the
complete capture/transport path, physical screen scanout or finger-to-game input
delay. Physical Late-judgment acceptance remains open.

The later [Surface presentation probe](STATUS.md#surface-frame-rate-hints-and-compositor-timestamps-2026-09-29)
matched ten individual frames by their exact release timestamps. Codec-reported
presentation was 5.35–5.72 ms later than SurfaceFlinger's actual-present timestamp
for those frames. This distinguishes two software timing sources; it does not
establish a fixed correction, a physical speedup or a value to enter in IF.
