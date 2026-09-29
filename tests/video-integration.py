"""Real WGC/GPU/H.264 smoke test against a project-owned window, with dry-run input."""

import argparse
import csv
from datetime import datetime, timezone
import json
import math
import pathlib
import re
import socket
import statistics
import struct
import subprocess
import threading
import time


HEADER = struct.Struct(">4sBBHIIQQQQHHHHII")
INPUT = struct.Struct(">4sBBBBIfQQ")


def unused_port():
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


def connect(port, process):
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError("Host exited before listening")
        try:
            return socket.create_connection(("127.0.0.1", port), timeout=0.5)
        except OSError:
            time.sleep(0.05)
    raise TimeoutError("Host did not listen")


def read_exact(stream, count):
    data = bytearray()
    while len(data) < count:
        chunk = stream.recv(count - len(data))
        if not chunk:
            raise EOFError("Host closed connection")
        data.extend(chunk)
    return data


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", type=pathlib.Path, default=pathlib.Path("dist/InFalsusTouchHost.exe"))
    parser.add_argument("--pattern", type=pathlib.Path, default=pathlib.Path("build/windows/tests/Release/ift_video_pattern.exe"))
    parser.add_argument("--seconds", type=float, default=10)
    parser.add_argument("--min-fps", type=float, default=0)
    parser.add_argument("--resolution", choices=("720p", "1080p"), default="720p")
    parser.add_argument("--fps", type=int, choices=range(24, 121), default=60)
    parser.add_argument("--output", type=pathlib.Path)
    args = parser.parse_args()
    if not 2 <= args.seconds <= 300:
        parser.error("--seconds must be within [2, 300]")
    dimensions = (1920, 1080) if args.resolution == "1080p" else (1280, 720)
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    output = args.output or pathlib.Path("build/video-test") / f"{args.resolution}{args.fps}-{run_id}"
    output.mkdir(parents=True, exist_ok=True)
    control_port, video_port = unused_port(), unused_port()
    while video_port == control_port:
        video_port = unused_port()
    pattern = subprocess.Popen([str(args.pattern.resolve()), args.resolution], stdout=subprocess.PIPE, text=True,
                               creationflags=subprocess.CREATE_NO_WINDOW)
    host = None
    try:
        window = pattern.stdout.readline().strip()
        if not window.startswith("0x"):
            raise RuntimeError("Test window did not provide its handle")
        with (output / "host.log").open("w", encoding="utf-8") as log:
            host = subprocess.Popen([str(args.host.resolve()), "--dry-run", "--video", "--window", window,
                                     "--no-profile", "--resolution", args.resolution, "--fps", str(args.fps),
                                     "--port", str(control_port), "--video-port", str(video_port),
                                     "--trace", str((output / "input-trace.txt").resolve())],
                                    stdout=log, stderr=log, creationflags=subprocess.CREATE_NO_WINDOW)
            with connect(video_port, host) as video, connect(control_port, host) as control:
                control.settimeout(1)
                video.settimeout(5)
                sequence = 0
                rtts = []
                control_lock = threading.Lock()
                heartbeat_stop = threading.Event()
                heartbeat_errors = []

                def send_input(message, lane=0):
                    nonlocal sequence
                    sequence += 1
                    sent = time.perf_counter_ns()
                    packet = INPUT.pack(b"IFT1", 3, message, lane, 0, sequence, 0.0, sent, 0)
                    control.sendall(packet)
                    ack = INPUT.unpack(read_exact(control, 32))
                    while ack[2] == 129:
                        ack = INPUT.unpack(read_exact(control, 32))
                    assert ack[2] == 128 and ack[5] == sequence and ack[7] == sent
                    rtts.append((time.perf_counter_ns() - sent) / 1e6)

                send_input(1)
                send_input(6)
                for lane in range(1, 7):
                    send_input(2, lane)

                def heartbeat():
                    try:
                        while not heartbeat_stop.wait(0.08):
                            with control_lock:
                                send_input(7)
                    except Exception as error:
                        heartbeat_errors.append(str(error))

                heartbeat_thread = threading.Thread(target=heartbeat, daemon=True)
                heartbeat_thread.start()
                started = None
                last_capture = 0
                count = 0
                keyframes = 0
                encoded_ms = []
                frame_age_ms = []
                timestamps = []
                total_bytes = 0
                with (output / "sample.h264").open("wb") as bitstream:
                    while started is None or time.monotonic() - started < args.seconds:
                        values = HEADER.unpack(read_exact(video, 64))
                        magic, version, kind, flags, size, seq, capture, encoded, sent, pts, width, height, fps, reserved, bitrate, tail = values
                        assert magic == b"IFV1" and version == 1 and reserved == tail == 0
                        assert 0 < size <= 4 * 1024 * 1024
                        payload = read_exact(video, size)
                        received = time.perf_counter_ns()
                        if kind == 3:
                            raise RuntimeError(payload.decode("utf-8"))
                        assert (width, height, fps) == (*dimensions, args.fps)
                        bitstream.write(payload)
                        if kind == 1:
                            assert seq == 0 and count == 0
                            units = re.split(b"\x00\x00(?:\x00)?\x01", payload)
                            sps = next(unit for unit in units if unit and unit[0] & 31 == 7)
                            assert sps[1] == 66, "Encoder must produce Baseline H.264 without B slices"
                            continue
                        assert kind == 2 and seq == count + 1 and capture > last_capture
                        assert capture <= encoded <= sent and pts == capture // 1000
                        if started is None:
                            started = time.monotonic()
                            assert flags == 1
                        last_capture = capture
                        count += 1
                        keyframes += flags & 1
                        total_bytes += size
                        encoded_ms.append((encoded - capture) / 1e6)
                        frame_age_ms.append((sent - capture) / 1e6)
                        timestamps.append((seq, capture, encoded, sent, received))
                heartbeat_stop.set()
                heartbeat_thread.join(timeout=2)
                assert not heartbeat_errors, heartbeat_errors
                with control_lock:
                    send_input(6)
                elapsed = time.monotonic() - started
                def intervals(column):
                    values = sorted((current[column] - previous[column]) / 1e6
                                    for previous, current in zip(timestamps, timestamps[1:]))
                    return {"samples": len(values), "mean": statistics.mean(values),
                            "p05": values[math.ceil(len(values) * 0.05) - 1],
                            "p95": values[math.ceil(len(values) * 0.95) - 1],
                            "min": values[0], "max": values[-1]}

                result = {
                    "width": dimensions[0], "height": dimensions[1], "targetFps": args.fps,
                    "frames": count, "seconds": elapsed, "fps": count / elapsed,
                    "megabitsPerSecond": total_bytes * 8 / elapsed / 1e6,
                    "keyframes": keyframes,
                    "captureToEncoderMeanMs": statistics.mean(encoded_ms),
                    "captureToSendMeanMs": statistics.mean(frame_age_ms),
                    "inputRttMedianMs": statistics.median(rtts),
                    "inputRttMaxMs": max(rtts),
                    "captureIntervalsMs": intervals(1),
                    "sendIntervalsMs": intervals(3),
                    "receiveIntervalsMs": intervals(4),
                }
                with (output / "frame-timestamps.csv").open("w", newline="", encoding="utf-8") as records:
                    writer = csv.writer(records)
                    writer.writerow(("sequence", "captureNs", "encodeNs", "sendNs", "receiveNs"))
                    writer.writerows(timestamps)
                (output / "metrics.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
                print(json.dumps(result, indent=2))
                assert count > 60 and keyframes >= 2, "Insufficient real encoded frames/keyframes"
                assert result["fps"] >= args.min_fps, "Video did not meet the requested frame-rate gate"
            time.sleep(0.1)
            trace = (output / "input-trace.txt").read_text(encoding="utf-8")
            for lane in range(1, 7):
                assert trace.count(f"DOWN {lane}\n") == trace.count(f"UP {lane}\n") == 1
            print("PASS: WGC -> GPU NV12 -> hardware H.264 -> TCP with concurrent six-key input")
            print(f"Evidence: {output.resolve()}")
    finally:
        for process in (host, pattern):
            if process is not None and process.poll() is None:
                process.terminate()
                process.wait(timeout=10)
        (output / "pattern.log").write_text(pattern.stdout.read(), encoding="utf-8")


if __name__ == "__main__":
    main()
