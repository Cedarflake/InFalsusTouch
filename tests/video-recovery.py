"""Resize/minimize our own D3D window; verify real decoded pixels and input isolation."""

import argparse
from datetime import datetime, timezone
import importlib.util
import json
import pathlib
import queue
import re
import shutil
import statistics
import struct
import subprocess
import threading
import time
import zlib


ROOT = pathlib.Path(__file__).resolve().parent.parent
SPEC = importlib.util.spec_from_file_location("video_integration", ROOT / "tests/video-integration.py")
BASE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BASE)
COLORS = ((235, 60, 60), (60, 210, 90), (60, 90, 235),
          (230, 205, 55), (200, 70, 200), (60, 200, 210))


class Pattern:
    def __init__(self, output, resolution):
        self.states = queue.Queue()
        self.log = (output / "pattern.log").open("w", encoding="utf-8")
        self.process = subprocess.Popen(
            [str(ROOT / "build/windows/tests/Release/ift_video_pattern.exe"), resolution, "--controlled"],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, creationflags=subprocess.CREATE_NO_WINDOW)
        self.reader = threading.Thread(target=self.read, daemon=True)
        self.reader.start()
        try:
            self.window = self.states.get(timeout=5)
            assert re.fullmatch(r"0x[0-9a-f]+", self.window), "Test window did not start"
        except Exception:
            self.close()
            raise

    def read(self):
        for line in self.process.stdout:
            self.log.write(line)
            self.log.flush()
            if line.startswith(("0x", "Pattern state:")):
                self.states.put(line.strip())

    def command(self, action, width, height, state="visible"):
        self.process.stdin.write(action + "\n")
        self.process.stdin.flush()
        result = self.states.get(timeout=3)
        match = re.fullmatch(r"Pattern state: (\w+) (\d+) (\d+) (visible|minimized)", result)
        assert match and match[1] == action.split()[0] and match[4] == state, result
        if state == "visible":
            assert (int(match[2]), int(match[3])) == (width, height), result

    def close(self):
        if self.process.poll() is None:
            self.process.stdin.close()
            try:
                self.process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                self.process.terminate()
                self.process.wait(timeout=5)
        self.reader.join(timeout=3)
        self.log.close()


class Video:
    def __init__(self, output, port, host, dimensions):
        self.output, self.port, self.host, self.dimensions = output, port, host, dimensions
        self.socket = None
        self.bitstream = None
        self.sessions = 0
        self.disconnects = []
        self.last_capture = 0

    def close(self):
        if self.socket:
            self.socket.close()
            self.socket = None
        if self.bitstream:
            self.bitstream.close()
            self.bitstream = None

    def read_exact(self, size, idle=False):
        data = bytearray()
        deadline = time.monotonic() + 0.5
        while len(data) < size:
            try:
                chunk = self.socket.recv(size - len(data))
            except TimeoutError:
                if not data and idle:
                    return None
                if time.monotonic() > deadline:
                    raise TimeoutError("Incomplete video packet")
                continue
            if not chunk:
                raise EOFError("Host closed video")
            data.extend(chunk)
        return data

    def poll(self):
        if not self.socket:
            self.socket = BASE.connect(self.port, self.host)
            BASE.subscribe(self.socket)
            self.socket.settimeout(0.1)
            self.sessions += 1
            self.sequence = 0
            self.configured = False
            self.bitstream = (self.output / f"session-{self.sessions}.h264").open("wb")
        try:
            header = self.read_exact(64, idle=True)
            if header is None:
                return None
            values = BASE.HEADER.unpack(header)
            magic, version, kind, flags, size, seq, capture, encoded, sent, pts, width, height, fps, reserved, bitrate, tail = values
            assert magic == b"IFV1" and version == 2 and reserved == tail == 0
            assert 0 < size <= 4 * 1024 * 1024
            payload = self.read_exact(size)
            if kind == 3:
                raise RuntimeError(payload.decode("utf-8"))
            assert (width, height, fps) == (*self.dimensions, 60)
            if kind == 1:
                assert seq == 0 and not self.configured
                units = re.split(b"\x00\x00(?:\x00)?\x01", payload)
                assert next(unit for unit in units if unit and unit[0] & 31 == 7)[1] == 66
                self.configured = True
            else:
                assert kind == 2 and self.configured and seq == self.sequence + 1
                assert capture > self.last_capture and capture <= encoded <= sent and pts == capture // 1000
                assert self.sequence != 0 or flags == 1, "Reconnect needs a fresh IDR"
                self.last_capture = capture
                self.sequence = seq
                frame = {"session": self.sessions, "index": seq - 1, "received": time.monotonic()}
            self.bitstream.write(payload)
            return frame if kind == 2 else None
        except (EOFError, ConnectionError) as error:
            self.disconnects.append(str(error))
            self.close()
            return None

    def collect(self, seconds):
        start = time.monotonic()
        frames = []
        while time.monotonic() - start < seconds:
            frame = self.poll()
            if frame:
                frames.append(frame)
        return start, frames


def write_png(path, rgb, width, height):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    rows = b"".join(b"\0" + rgb[y * width * 3:(y + 1) * width * 3] for y in range(height))
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(rows)) + chunk(b"IEND", b""))


def check_pixels(rgb, dimensions, source):
    width, height = dimensions
    scale = min(width / source[0], height / source[1])
    fitted = (int(source[0] * scale) & ~1, int(source[1] * scale) & ~1)
    left, top = (width - fitted[0]) // 2, (height - fitted[1]) // 2

    def pixel(x, y):
        offset = (y * width + x) * 3
        return tuple(rgb[offset:offset + 3])

    samples = [pixel(left + (lane * 2 + 1) * fitted[0] // 12, top + fitted[1] // 16) for lane in range(6)]
    for actual, expected in zip(samples, COLORS):
        assert all(abs(a - b) < 55 for a, b in zip(actual, expected)), (source, actual, expected)
    for x, y in ((left // 2, height // 2), (width - left // 2 - 1, height // 2)) if left else ():
        assert max(pixel(x, y)) < 15, ("Side bar is not black", source, pixel(x, y))
    for x, y in ((width // 2, top // 2), (width // 2, height - top // 2 - 1)) if top else ():
        assert max(pixel(x, y)) < 15, ("Top/bottom bar is not black", source, pixel(x, y))
    marker = [x for x in range(left, left + fitted[0]) if min(pixel(x, height // 2)) > 245]
    assert marker, "Decoded animation marker is missing"
    return {"fit": [left, top, *fitted], "colors": samples, "markerX": statistics.mean(marker)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--resolution", choices=("720p", "1080p"), default="720p")
    parser.add_argument("--ffmpeg", default=shutil.which("ffmpeg"))
    parser.add_argument("--output", type=pathlib.Path)
    args = parser.parse_args()
    if not args.ffmpeg:
        parser.error("ffmpeg is required for decoded-pixel checks")
    dimensions = (1920, 1080) if args.resolution == "1080p" else (1280, 720)
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    output = args.output or ROOT / "build/video-recovery-test" / f"{args.resolution}-{run_id}"
    output.mkdir(parents=True, exist_ok=True)
    control_port, video_port = BASE.unused_port(), BASE.unused_port()
    while control_port == video_port:
        video_port = BASE.unused_port()
    pattern = Pattern(output, args.resolution)
    host = video = control = heartbeat_thread = None
    heartbeat_stop = threading.Event()
    heartbeat_errors, rtts, ack_times, phases = [], [], [], []
    result = {"resolution": args.resolution, "phases": phases, "passed": False}
    try:
        with (output / "host.log").open("w", encoding="utf-8") as log:
            host = subprocess.Popen([str(ROOT / "dist/InFalsusTouchHost.exe"), "--dry-run", "--video", "--no-profile",
                                     "--window", pattern.window, "--resolution", args.resolution,
                                     "--port", str(control_port), "--video-port", str(video_port),
                                     "--trace", str(output / "input-trace.txt")],
                                    stdout=log, stderr=log, creationflags=subprocess.CREATE_NO_WINDOW)
            control = BASE.connect(control_port, host)
            control.settimeout(1)
            sequence = 0

            def send(message, lane=0):
                nonlocal sequence
                sequence += 1
                sent = time.perf_counter_ns()
                control.sendall(BASE.INPUT.pack(b"IFT1", 3, message, lane, 0, sequence, 0.0, sent, 0))
                ack = BASE.INPUT.unpack(BASE.read_exact(control, 32))
                while ack[2] == 129:
                    ack = BASE.INPUT.unpack(BASE.read_exact(control, 32))
                assert ack[2] == 128 and ack[5] == sequence and ack[7] == sent
                rtts.append((time.perf_counter_ns() - sent) / 1e6)
                ack_times.append(time.monotonic())

            send(1)
            send(6)
            for lane in range(1, 7):
                send(2, lane)

            def heartbeat():
                try:
                    while not heartbeat_stop.wait(0.08):
                        send(7)
                except Exception as error:
                    heartbeat_errors.append(str(error))

            heartbeat_thread = threading.Thread(target=heartbeat, daemon=True)
            heartbeat_thread.start()
            video = Video(output, video_port, host, dimensions)

            def phase(name, source):
                started, frames = video.collect(3)
                steady = [frame for frame in frames if frame["received"] >= started + 2]
                assert len(steady) >= 50, f"{name}: did not recover to 50 FPS within two seconds"
                samples = [next(frame for frame in frames if frame["received"] >= started + 1.5), frames[-1]]
                phases.append({"name": name, "source": source, "frames": len(frames),
                               "finalSecondFrames": len(steady),
                               "firstPacketMs": (frames[0]["received"] - started) * 1000,
                               "samples": samples})
                print(f"{name}: {len(frames)} frames; {len(steady)} in final second", flush=True)

            phase("initial", dimensions)
            for name, source in (("smaller", (960, 540)), ("square", (900, 900)), ("wide", (1280, 480))):
                pattern.command(f"resize {source[0]} {source[1]}", *source)
                phase(name, source)
            pattern.command("minimize", 1280, 480, "minimized")
            started, minimized = video.collect(2)
            assert not [frame for frame in minimized if frame["received"] > started + 0.5], "Capture continued while minimized"
            result["minimizedSeconds"] = time.monotonic() - started
            pattern.command("restore", 1280, 480)
            phase("restored", (1280, 480))
            pattern.command(f"resize {dimensions[0]} {dimensions[1]}", *dimensions)
            phase("original", dimensions)
            video.close()
            phase("reconnected", dimensions)
            heartbeat_stop.set()
            heartbeat_thread.join(timeout=2)
            assert not heartbeat_thread.is_alive() and not heartbeat_errors, heartbeat_errors
            send(6)
            control.close()
            control = None
            video.close()
            result.update(sessions=video.sessions, unexpectedDisconnects=video.disconnects,
                          inputAcks=len(rtts), inputRttMedianMs=statistics.median(rtts), inputRttMaxMs=max(rtts),
                          inputMaxAckGapMs=max(b - a for a, b in zip(ack_times, ack_times[1:])) * 1000)
            assert result["inputMaxAckGapMs"] < 500, "Video recovery starved the input heartbeat"
            time.sleep(0.1)
            trace = (output / "input-trace.txt").read_text(encoding="utf-8")
            for lane in range(1, 7):
                assert trace.count(f"DOWN {lane}\n") == trace.count(f"UP {lane}\n") == 1, f"Lane {lane} changed during video recovery"
        for session in range(1, video.sessions + 1):
            selected = sorted((sample["index"], item, sample) for item in phases for sample in item["samples"]
                              if sample["session"] == session)
            if not selected:
                continue
            expression = "+".join(f"eq(n,{index})" for index, _, _ in selected)
            decoded = subprocess.run([args.ffmpeg, "-v", "error", "-threads", "1", "-i", str(output / f"session-{session}.h264"),
                                      "-vf", f"select='{expression}'", "-fps_mode", "passthrough", "-pix_fmt", "rgb24",
                                      "-f", "rawvideo", "-"], capture_output=True, check=True, timeout=30,
                                     creationflags=subprocess.CREATE_NO_WINDOW).stdout
            size = dimensions[0] * dimensions[1] * 3
            assert len(decoded) == len(selected) * size, "Decoder did not produce the selected frames"
            for offset, (index, item, sample) in enumerate(selected):
                rgb = decoded[offset * size:(offset + 1) * size]
                sample["pixels"] = check_pixels(rgb, dimensions, item["source"])
                write_png(output / f"{item['name']}-{index}.png", rgb, *dimensions)
        for item in phases:
            markers = [sample["pixels"]["markerX"] for sample in item["samples"]]
            assert abs(markers[0] - markers[1]) > 10, f"{item['name']}: decoded picture appears frozen"
        result["passed"] = True
        print("PASS: resize, centered fit pixels, minimize/restore, fresh-IDR reconnect and independent input")
    finally:
        heartbeat_stop.set()
        if heartbeat_thread:
            heartbeat_thread.join(timeout=2)
        if control:
            control.close()
        if video:
            video.close()
        if host is not None and host.poll() is None:
            host.terminate()
            host.wait(timeout=5)
        pattern.close()
        (output / "metrics.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        print(f"Evidence: {output}", flush=True)


if __name__ == "__main__":
    main()
