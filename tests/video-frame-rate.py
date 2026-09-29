"""Verify on-demand frame-rate groups, isolated subscriptions and phone-driven FPS."""

import argparse
from collections import Counter
from contextlib import nullcontext
import hashlib
import threading
import re
from datetime import datetime, timezone
import importlib.util
import json
import pathlib
import socket
import struct
import subprocess
import time
import xml.etree.ElementTree as ET


ROOT = pathlib.Path(__file__).resolve().parent.parent
HEADER = struct.Struct(">4sBBHIIQQQQHHHHII")


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / "tests" / filename)
    loaded = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(loaded)
    return loaded


device = module("device", "video-device.py")
multiplayer = module("multiplayer", "multiplayer-integration.py")


def preferences(raw):
    values = {item.attrib["name"]: item.attrib.get("value", item.text) for item in ET.fromstring(raw)}
    old = values.pop("highRefreshDisplay", "true")
    values.setdefault("videoFps", "120" if old == "true" else "60")
    return values


def read_exact(stream, count):
    data = bytearray()
    while len(data) < count:
        chunk = stream.recv(count - len(data))
        if not chunk:
            raise EOFError("Video closed")
        data.extend(chunk)
    return data


class Viewer:
    def __init__(self, port, fps, output, name, fragmented=False, record=True):
        self.fps = fps or 30
        self.name = name
        self.records = []
        self.timings = []
        self.lock = threading.Lock()
        self.failure = None
        self.closing = threading.Event()
        self.ready = threading.Event()
        self.socket = socket.create_connection(("127.0.0.1", port), timeout=8)
        request = struct.pack(">4sBBH", b"IFV1", 2, 0, fps)
        if fragmented:
            for byte in request:
                self.socket.sendall(bytes([byte]))
                time.sleep(0.005)
        else:
            self.socket.sendall(request)
        filename = output / f"{name}.h264" if record else None
        self.thread = threading.Thread(target=self.receive, args=(filename,), daemon=True)
        self.thread.start()
        if not self.ready.wait(10) or self.failure:
            self.close()
            raise AssertionError(f"{name} never started: {self.failure}")

    def receive(self, filename):
        try:
            with (filename.open("wb") if filename else nullcontext()) as recording:
                header = HEADER.unpack(read_exact(self.socket, HEADER.size))
                assert header[:3] == (b"IFV1", 2, 1) and header[5] == 0 and header[12] == self.fps, header
                assert 0 < header[4] <= 65536
                parameters = read_exact(self.socket, header[4])
                if recording is not None:
                    recording.write(parameters)
                sequence = 0
                last_capture = 0
                while not self.closing.is_set():
                    header = HEADER.unpack(read_exact(self.socket, HEADER.size))
                    assert header[:3] == (b"IFV1", 2, 2) and header[12] == self.fps, header
                    assert header[5] == sequence + 1 and 0 < header[4] <= 4 * 1024 * 1024, header
                    assert last_capture < header[6] <= header[7] <= header[8], header
                    assert sequence != 0 or header[3] == 1, "New viewer needs an IDR"
                    payload = read_exact(self.socket, header[4])
                    received_at = time.perf_counter_ns()
                    if recording is not None:
                        recording.write(payload)
                    sequence += 1
                    last_capture = header[6]
                    with self.lock:
                        self.records.append((header[6], header[7], hashlib.sha256(payload).hexdigest()))
                        self.timings.append((header[6], header[7], received_at))
                    self.ready.set()
        except Exception as error:
            if not self.closing.is_set():
                self.failure = repr(error)
                self.ready.set()

    def snapshot(self):
        assert self.failure is None, (self.name, self.failure)
        with self.lock:
            return list(self.records)

    def close(self):
        self.closing.set()
        try:
            self.socket.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        self.socket.close()
        self.thread.join(3)
        assert not self.thread.is_alive(), "Video reader did not stop"


def rates(viewers, seconds=3):
    starts = [len(viewer.snapshot()) for viewer in viewers]
    time.sleep(seconds)
    values = {}
    for viewer, start in zip(viewers, starts):
        frames = viewer.snapshot()[start:]
        assert len(frames) > 1, (viewer.name, "no progress")
        fps = (len(frames) - 1) * 1e9 / (frames[-1][0] - frames[0][0])
        assert fps >= viewer.fps * 0.9, (viewer.name, fps, viewer.fps)
        values[viewer.name] = fps
    return values


def wait_for(predicate, message, timeout=8):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(0.05)
    raise AssertionError(message)


def verify_peers(control_port, video_port, trace, output):
    log = output / "host.log"
    text = lambda: log.read_text(encoding="utf-8")
    def created():
        return Counter(re.findall(r"Video group (\d+) FPS created", text()))
    for request in (b"INVALID!", struct.pack(">4sBBH", b"IFV1", 2, 0, 121)):
        with socket.create_connection(("127.0.0.1", video_port), timeout=3) as stream:
            stream.sendall(request)
            try:
                assert not stream.recv(1), "Malformed subscription was accepted"
            except ConnectionResetError:
                pass
    with socket.create_connection(("127.0.0.1", video_port), timeout=3) as partial:
        partial.sendall(b"IFV1")
        assert not partial.recv(1), "Incomplete subscription was accepted"
    assert not created() and "Video capture started" not in text(), "Idle host allocated video resources"
    peers = []
    controller = multiplayer.Peer(control_port, fps=120)
    metrics = {}
    try:
        controller.request(2, lane=1)
        time.sleep(0.1)
        assert not created(), "Input-only peer allocated an encoder"
        first = Viewer(video_port, 120, output, "first-120", fragmented=True)
        peers.append(first)
        metrics["single"] = rates([first], 2)
        assert created() == {"120": 1}, created()
        same = Viewer(video_port, 120, output, "same-120")
        peers.append(same)
        low = Viewer(video_port, 60, output, "low-60")
        peers.append(low)
        middle = Viewer(video_port, 90, output, "middle-90")
        peers.append(middle)
        metrics["mixed"] = rates(peers, 4)
        assert created() == {"120": 1, "60": 1, "90": 1}, created()
        a = {row[0]: row[1:] for row in first.snapshot()}
        b = {row[0]: row[1:] for row in same.snapshot()}
        common = a.keys() & b.keys()
        assert len(common) >= 120 and all(a[key] == b[key] for key in common), "Same-rate viewers did not share encoded bytes"
        same.close()
        peers.remove(same)
        low.close()
        peers.remove(low)
        wait_for(lambda: "Video group 60 FPS stopped" in text(), "Unused 60 FPS encoder stayed alive")
        moved = Viewer(video_port, 90, output, "moved-to-90")
        peers.append(moved)
        metrics["moveToExistingGroup"] = rates(peers)
        assert created() == {"120": 1, "60": 1, "90": 1}, created()
        middle.close()
        peers.remove(middle)
        assert "Video group 90 FPS stopped" not in text(), "An occupied encoder was released"
        moved.close()
        peers.remove(moved)
        wait_for(lambda: "Video group 90 FPS stopped" in text(), "Unused 90 FPS encoder stayed alive")
        metrics["remaining120"] = rates(peers, 2)
        records = first.snapshot()
        metrics["maximum120CaptureGapMs"] = max((b[0] - a[0]) / 1e6 for a, b in zip(records, records[1:]))
        assert metrics["maximum120CaptureGapMs"] < 150, metrics
        assert text().count("Video capture started") == 1, "Group changes restarted shared capture"
        assert "UP 1" not in trace.read_text(), "Video subscription changes released held input"
        first.close()
        peers.remove(first)
        wait_for(lambda: "Video group 120 FPS stopped" in text() and "Video capture stopped" in text(), "Idle resources were not released")
        controller.request(3, lane=1)
        assert not controller.failure, controller.failure
        fallback = Viewer(video_port, 0, output, "fallback-30")
        peers.append(fallback)
        metrics["diagnosticFallback"] = rates([fallback], 2)
        fallback.close()
        peers.remove(fallback)
        wait_for(lambda: "Video group 30 FPS stopped" in text(), "Fallback group stayed alive")
        assert created() == {"120": 1, "60": 1, "90": 1, "30": 1}, created()
        metrics["groupCreations"] = dict(created())
        metrics["sharedEncodedFrames"] = len(common)
        metrics["physicalPhones"] = 0
        (output / "groups.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")
        print(json.dumps(metrics, indent=2), flush=True)
        print("PASS: on-demand 60/90/120 groups, identical same-rate bitstreams, isolated changes, idle release, held input", flush=True)
    finally:
        for peer in peers:
            peer.close()
        controller.close()


def verify_device(output, control_port, video_port, skip_install):
    before_mappings = device.mappings()
    before = device.adb("exec-out", "run-as", device.APP, "cat", "shared_prefs/controller-settings.xml", binary=True)
    (output / "preferences-before.xml").write_bytes(before)
    assigned = {"tcp:27184": f"tcp:{control_port}", "tcp:27183": f"tcp:{video_port}"}
    try:
        if not skip_install:
            for apk in device.apks("profile"):
                print(device.adb("install", "-r", "-t", str(apk)), flush=True)
        device.adb("shell", "am", "force-stop", device.APP)
        for local, remote in assigned.items():
            device.adb("reverse", local, remote)
        result = device.adb("shell", "am", "instrument", "-w", "-r", "-e", "videoFrameRateTest", "true",
            "-e", "class", "dev.cedarflake.ift.SettingsDeviceTest#phoneSelectionChangesHostVideoAndSurvivesRelaunch",
            f"{device.APP}.test/androidx.test.runner.AndroidJUnitRunner", timeout=180)
        (output / "instrumentation.txt").write_text(result, encoding="utf-8")
        print(result, flush=True)
        assert "OK (1 test)" in result, result
        for filename in ("video-frame-rate.json", "video-frame-rate-settings.png"):
            (output / filename).write_bytes(device.adb("exec-out", "run-as", device.APP, "cat", f"files/{filename}", binary=True))
        print((output / "video-frame-rate.json").read_text(), flush=True)
    finally:
        device.adb("shell", "am", "force-stop", device.APP)
        current = device.mappings()
        for local, remote in assigned.items():
            if current.get(local) == remote:
                if local in before_mappings:
                    device.adb("reverse", local, before_mappings[local])
                else:
                    device.adb("reverse", "--remove", local)
        after = device.adb("exec-out", "run-as", device.APP, "cat", "shared_prefs/controller-settings.xml", binary=True)
        (output / "preferences-after.xml").write_bytes(after)
        restored = {"settingsRestored": preferences(before) == preferences(after), "mappingsRestored": before_mappings == device.mappings()}
        (output / "restored.json").write_text(json.dumps(restored), encoding="utf-8")
        device.adb("shell", "am", "start", "-n", device.APP + "/dev.cedarflake.ift.MainActivity")
        assert all(restored.values()), restored


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--device", action="store_true")
    parser.add_argument("--skip-install", action="store_true")
    parser.add_argument("--window", type=lambda value: int(value, 0))
    args = parser.parse_args()
    output = ROOT / "build/video-frame-rate" / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    output.mkdir(parents=True)
    control_port, video_port = device.port(), device.port()
    while control_port == video_port:
        video_port = device.port()
    pattern = host = None
    try:
        if args.window is None:
            pattern = subprocess.Popen([str(ROOT / "build/windows/tests/Release/ift_video_pattern.exe")],
                stdout=subprocess.PIPE, text=True, creationflags=subprocess.CREATE_NO_WINDOW)
            window = pattern.stdout.readline().strip()
        else:
            window = hex(args.window)
        with (output / "host.log").open("w", encoding="utf-8") as log:
            trace = output / "input.txt"
            host = subprocess.Popen([str(ROOT / "build/windows/windows/Release/InFalsusTouchHost.exe"),
                "--dry-run", "--video", "--no-profile", "--window", window, "--fps", "30", "--trace", str(trace),
                "--port", str(control_port), "--video-port", str(video_port)],
                stdout=log, stderr=log, creationflags=subprocess.CREATE_NO_WINDOW)
            wait_for(lambda: "Video listening" in (output / "host.log").read_text(encoding="utf-8"), "Host did not listen")
            verify_peers(control_port, video_port, trace, output)
            if args.device:
                observer = Viewer(video_port, 60, output, "phone-companion-60")
                try:
                    verify_device(output, control_port, video_port, args.skip_install)
                    companion = rates([observer], 2)
                    assert (output / "host.log").read_text(encoding="utf-8").count("Video group 60 FPS created") == 2, "Phone changes restarted the companion's encoder"
                    (output / "companion.json").write_text(json.dumps(companion, indent=2), encoding="utf-8")
                    print("PASS: phone changes and reconnects preserved the companion's 60 FPS stream", flush=True)
                finally:
                    observer.close()
    finally:
        for process in (host, pattern):
            if process is not None and process.poll() is None:
                process.terminate()
                process.wait(timeout=10)
    print(f"Evidence: {output}", flush=True)


if __name__ == "__main__":
    main()
