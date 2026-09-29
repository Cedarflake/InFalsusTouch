"""Exercise an existing Host during play using video-only simulated receivers."""

import argparse
from datetime import datetime, timezone
import importlib.util
import json
import pathlib
import socket
import struct
import time


ROOT = pathlib.Path(__file__).resolve().parent.parent
SPEC = importlib.util.spec_from_file_location("frame_rate", ROOT / "tests/video-frame-rate.py")
BASE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(BASE)


def percentile(values, percent):
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int((len(ordered) - 1) * percent / 100))]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=27183)
    parser.add_argument("--fps", type=int, choices=(60, 90, 120), default=120)
    parser.add_argument("--seconds", type=int, choices=range(3, 31), default=8)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--baseline-only", action="store_true")
    mode.add_argument("--churn-only", action="store_true")
    args = parser.parse_args()
    output = ROOT / "build/video-live-groups" / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    output.mkdir(parents=True)
    viewers = {}
    phases = []
    operations = []
    clock_offset = time.time_ns() - time.perf_counter_ns()
    slow = None
    completed = False
    maximum_receivers = 0

    def connect(name, fps):
        operation = {"action": "connect", "viewer": name, "fps": fps, "startedUtc": datetime.now(timezone.utc).isoformat()}
        viewers[name] = BASE.Viewer(args.port, fps, output, name, record=False)
        operation["completedUtc"] = datetime.now(timezone.utc).isoformat()
        operations.append(operation)
        (output / "operations.json").write_text(json.dumps(operations, indent=2), encoding="utf-8")

    def disconnect(name):
        operations.append({"action": "disconnect", "viewer": name, "utc": datetime.now(timezone.utc).isoformat()})
        viewers.pop(name).close()
        (output / "operations.json").write_text(json.dumps(operations, indent=2), encoding="utf-8")

    def sample(name):
        nonlocal maximum_receivers
        maximum_receivers = max(maximum_receivers, len(viewers) + int(slow is not None))
        started = datetime.now(timezone.utc).isoformat()
        starts = {key: len(viewer.snapshot()) for key, viewer in viewers.items()}
        time.sleep(args.seconds)
        streams = {}
        for key, viewer in viewers.items():
            viewer.snapshot()
            with viewer.lock:
                rows = viewer.timings[starts[key]:]
            assert len(rows) > 1, (name, key, "No video progress")
            gap_start, gap_end = max(zip(rows, rows[1:]), key=lambda pair: pair[1][0] - pair[0][0])
            streams[key] = {
                "targetFps": viewer.fps,
                "fps": (len(rows) - 1) * 1e9 / (rows[-1][0] - rows[0][0]),
                "encodeP95Ms": percentile([(row[1] - row[0]) / 1e6 for row in rows], 95),
                "encodeMaxMs": max((row[1] - row[0]) / 1e6 for row in rows),
                "localReceiveP95Ms": percentile([(row[2] - row[0]) / 1e6 for row in rows], 95),
                "maxCaptureGapMs": (gap_end[0] - gap_start[0]) / 1e6,
                "maxCaptureGapAtUtc": datetime.fromtimestamp((gap_end[0] + clock_offset) / 1e9, timezone.utc).isoformat(),
            }
        phase = {"phase": name, "startedUtc": started, "utc": datetime.now(timezone.utc).isoformat(), "streams": streams}
        phases.append(phase)
        (output / "metrics.json").write_text(json.dumps(phases, indent=2), encoding="utf-8")
        print(json.dumps(phase), flush=True)

    try:
        connect("continuous", args.fps)
        sample("baseline")
        if args.baseline_only:
            sample("baseline-repeat")
            completed = True
            return
        if args.churn_only:
            for index, fps in enumerate((60, 90, 60, 90, 60, 90)):
                connect("churn", fps)
                sample(f"churn-{index + 1}-{fps}")
                disconnect("churn")
                sample(f"released-{index + 1}")
            sample("back-to-baseline")
            completed = True
            return
        connect("low", 60)
        sample("join-60")
        connect("middle", 90)
        sample("join-90")
        connect("same-120", 120)
        connect("same-60", 60)
        sample("reuse-existing-groups")
        slow = socket.socket()
        slow.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 4096)
        slow.settimeout(3)
        slow.connect(("127.0.0.1", args.port))
        slow.sendall(struct.pack(">4sBBH", b"IFV1", 2, 0, 60))
        sample("slow-sixth-simulated-receiver")
        slow.close()
        slow = None
        disconnect("same-60")
        disconnect("low")
        connect("low", 90)
        sample("60-switches-to-existing-90")
        disconnect("middle")
        disconnect("low")
        sample("release-90")
        for index in range(3):
            connect("churn", 60 if index % 2 == 0 else 90)
            sample(f"recreate-group-{index + 1}")
            disconnect("churn")
        disconnect("same-120")
        sample("back-to-baseline")
        completed = True
    finally:
        if slow is not None:
            slow.close()
        for viewer in viewers.values():
            if viewer.name == "continuous":
                with viewer.lock:
                    timings = list(viewer.timings)
                with (output / "continuous-timings.csv").open("w", encoding="utf-8") as trace:
                    trace.write("captureNs,encodeNs,localReceiveNs\n")
                    trace.writelines(",".join(map(str, row)) + "\n" for row in timings)
                summary = {
                    "completed": completed,
                    "receiverFailure": viewer.failure,
                    "receivedFrames": len(timings),
                    "maxCaptureGapIncludingTransitionsMs": max(((b[0] - a[0]) / 1e6 for a, b in zip(timings, timings[1:])), default=None),
                    "maximumSimulatedReceivers": maximum_receivers,
                    "phoneLatencyMeasured": False,
                }
                (output / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
                print(json.dumps(summary), flush=True)
            viewer.close()
        print(f"Evidence: {output}", flush=True)


if __name__ == "__main__":
    main()
