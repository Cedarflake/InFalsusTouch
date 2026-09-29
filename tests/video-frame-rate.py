"""Verify phone-driven FPS, live changes and shared-stream negotiation without game input."""

import argparse
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


def stream_fps(port, expected):
    deadline = time.monotonic() + 12
    while time.monotonic() < deadline:
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=5) as stream:
                def read(count):
                    data = bytearray()
                    while len(data) < count:
                        chunk = stream.recv(count - len(data))
                        if not chunk:
                            raise EOFError("Video restarted")
                        data.extend(chunk)
                    return data
                header = HEADER.unpack(read(HEADER.size))
                assert header[:3] == (b"IFV1", 1, 1), header
                assert 0 < header[4] <= 65536
                read(header[4])
                if header[12] == expected:
                    frame = HEADER.unpack(read(HEADER.size))
                    assert frame[2] == 2 and frame[3] == 1 and frame[5] == 1, frame
                    assert frame[12] == expected and 0 < frame[4] <= 4 * 1024 * 1024
                    read(frame[4])
                    return
        except (OSError, EOFError):
            pass
        time.sleep(0.05)
    raise AssertionError(f"Video did not negotiate {expected} FPS")


def verify_peers(control_port, video_port, trace):
    peers = []
    try:
        first = multiplayer.Peer(control_port, fps=120)
        peers.append(first)
        stream_fps(video_port, 120)
        first.request(2, lane=1)
        first.request(11, value=60)
        stream_fps(video_port, 60)
        assert "DOWN 1" in trace.read_text() and "UP 1" not in trace.read_text(), "FPS change released a held key"
        first.request(11, value=120)
        second = multiplayer.Peer(control_port, fps=60)
        peers.append(second)
        stream_fps(video_port, 60)
        second.request(11, value=90)
        stream_fps(video_port, 90)
        second.close()
        peers.pop()
        stream_fps(video_port, 120)
        assert not first.failure, first.failure
        assert "UP 1" not in trace.read_text(), "Another phone's disconnect released a held key"
        first.request(3, lane=1)
        first.close()
        peers.pop()
        stream_fps(video_port, 30)
        print("PASS: phone overrides Host fallback; 120/60/90 live changes; peer leave; held input preserved", flush=True)
    finally:
        for peer in peers:
            peer.close()


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
            stream_fps(video_port, 30)
            verify_peers(control_port, video_port, trace)
            if args.device:
                verify_device(output, control_port, video_port, args.skip_install)
    finally:
        for process in (host, pattern):
            if process is not None and process.poll() is None:
                process.terminate()
                process.wait(timeout=10)
    print(f"Evidence: {output}", flush=True)


if __name__ == "__main__":
    main()
