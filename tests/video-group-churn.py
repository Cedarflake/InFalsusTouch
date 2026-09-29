"""Measure uninterrupted streams across controller joins, disconnects and FPS switches."""

import argparse
from collections import Counter
from datetime import datetime, timezone
import importlib.util
import json
import pathlib
import re
import subprocess
import time


ROOT = pathlib.Path(__file__).resolve().parent.parent
SPEC = importlib.util.spec_from_file_location("frame_rate", ROOT / "tests/video-frame-rate.py")
BASE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BASE)


def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int((len(ordered) - 1) * fraction))]


def verify(control_port, video_port, output, fps, cycles, max_gap_ms):
    viewers = {}
    controllers = {}
    operations = []
    log = output / "host.log"

    def counts():
        return Counter(re.findall(r"Video group (\d+) FPS created", log.read_text(encoding="utf-8")))

    def connect(name, rate):
        controllers[name] = BASE.multiplayer.Peer(control_port, fps=rate)
        viewers[name] = BASE.Viewer(video_port, rate, output, name, record=False)

    def disconnect(name):
        viewers.pop(name).close()
        controllers.pop(name).close()

    def operation(name, action, settle=0.25):
        start = time.perf_counter_ns()
        action()
        time.sleep(settle)
        end = time.perf_counter_ns()
        viewers["continuous"].snapshot()
        operations.append({"action": name, "startNs": start, "endNs": end})

    continuous = None
    completed = False
    try:
        connect("continuous", fps)
        continuous = viewers["continuous"]
        operation("baseline", lambda: time.sleep(2))
        other_rates = (60, 90) if fps == 120 else (90, 120)
        for cycle in range(cycles):
            for rate in other_rates:
                operation(f"join-{cycle}-{rate}", lambda: connect("switching", rate))
                operation(f"disconnect-{cycle}-{rate}", lambda: disconnect("switching"))
        assert counts() == {str(rate): 1 for rate in (60, 90, 120)}, (
            "Rapid reconnects recreated encoders", counts())
        for index in range(6):
            operation(f"shared-join-{index}", lambda: connect(f"shared-{index}", fps), 0.1)
        for index in range(6):
            operation(f"shared-leave-{index}", lambda: disconnect(f"shared-{index}"), 0.1)
        operation("idle-retention", lambda: time.sleep(2.2))
        for rate in other_rates:
            assert f"Video group {rate} FPS stopped" not in log.read_text(encoding="utf-8"), "Idle teardown disturbed an active session"
        operation("idle-rejoin", lambda: connect("switching", other_rates[0]))
        operation("idle-disconnect", lambda: disconnect("switching"))
        assert counts() == {str(rate): 1 for rate in (60, 90, 120)}, "Idle group was not reused"
        operation("new-rate", lambda: connect("switching", 30))
        operation("new-rate-disconnect", lambda: disconnect("switching"))
        operation("after", lambda: time.sleep(2.2))
        assert log.read_text(encoding="utf-8").count("Video capture started") == 1, "Peer changes restarted capture"
        assert counts()[str(fps)] == 1, "Peer changes restarted the continuous encoder"
        assert all(peer.failure is None for peer in controllers.values())
        completed = True
    finally:
        if continuous is not None:
            with continuous.lock:
                timings = list(continuous.timings)
            (output / "continuous-timings.csv").write_text(
                "captureNs,encodeNs,receiveNs\n" + "".join(",".join(map(str, row)) + "\n" for row in timings), encoding="utf-8")
            for operation in operations:
                pairs = [(a, b) for a, b in zip(timings, timings[1:])
                         if a[2] <= operation["endNs"] and b[2] >= operation["startNs"]]
                if pairs:
                    operation.update(
                        maxCaptureGapMs=max((b[0] - a[0]) / 1e6 for a, b in pairs),
                        maxReceiveGapMs=max((b[2] - a[2]) / 1e6 for a, b in pairs),
                        receiveGapP99Ms=percentile([(b[2] - a[2]) / 1e6 for a, b in pairs], 0.99),
                    )
            (output / "operations.json").write_text(json.dumps(operations, indent=2), encoding="utf-8")
        for name in list(viewers):
            disconnect(name)
        for peer in controllers.values():
            peer.close()
        if completed:
            BASE.wait_for(lambda: all(f"Video group {rate} FPS stopped" in log.read_text(encoding="utf-8") for rate in counts()),
                "Idle session retained encoders")
    assert completed and len(timings) > 1
    start = operations[0]["startNs"]
    rows = [row for row in timings if row[2] >= start]
    gaps = [(b[2] - a[2]) / 1e6 for a, b in zip(rows, rows[1:])]
    summary = {
        "targetFps": fps,
        "receivedFps": (len(rows) - 1) * 1e9 / (rows[-1][0] - rows[0][0]),
        "maxReceiveGapIncludingTransitionsMs": max(gaps),
        "receiveGapP99Ms": percentile(gaps, 0.99),
        "operations": len(operations),
        "maximumSimulatedControllers": 7,
        "groupCreations": dict(counts()),
        "physicalPhones": 0,
    }
    (output / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2), flush=True)
    assert summary["receivedFps"] >= fps * 0.95, summary
    assert summary["maxReceiveGapIncludingTransitionsMs"] <= max_gap_ms, summary
    assert summary["receiveGapP99Ms"] <= 1000 / fps + 6, summary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", type=pathlib.Path, default=ROOT / "build/windows/windows/Release/InFalsusTouchHost.exe")
    parser.add_argument("--window", type=lambda value: int(value, 0))
    parser.add_argument("--fps", type=int, choices=(60, 120), default=120)
    parser.add_argument("--cycles", type=int, choices=range(2, 41), default=8)
    parser.add_argument("--max-gap-ms", type=float, default=50)
    args = parser.parse_args()
    output = ROOT / "build/video-group-churn" / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    output.mkdir(parents=True)
    pattern = host = None
    control_port, video_port = BASE.device.port(), BASE.device.port()
    while control_port == video_port:
        video_port = BASE.device.port()
    try:
        if args.window is None:
            pattern = subprocess.Popen([str(ROOT / "build/windows/tests/Release/ift_video_pattern.exe"), "720p", "--controlled"],
                stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True, creationflags=subprocess.CREATE_NO_WINDOW)
            window = pattern.stdout.readline().strip()
        else:
            window = hex(args.window)
        with (output / "host.log").open("w", encoding="utf-8") as log:
            host = subprocess.Popen([str(args.host.resolve()), "--dry-run", "--video", "--no-profile", "--window", window,
                "--fps", "120", "--port", str(control_port), "--video-port", str(video_port), "--trace", str(output / "input.txt")],
                stdout=log, stderr=log, creationflags=subprocess.CREATE_NO_WINDOW)
            BASE.wait_for(lambda: "Video listening" in (output / "host.log").read_text(encoding="utf-8"), "Host did not listen")
            verify(control_port, video_port, output, args.fps, args.cycles, args.max_gap_ms)
    finally:
        for process in (host, pattern):
            if process is not None and process.poll() is None:
                process.terminate()
                process.wait(timeout=10)
        print(f"Evidence: {output}", flush=True)


if __name__ == "__main__":
    main()
