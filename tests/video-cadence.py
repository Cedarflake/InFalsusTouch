"""Measure per-second receive, decode and presentation gaps over real USB video."""

import argparse
from datetime import datetime, timezone
import importlib.util
import json
import pathlib
import re
import subprocess


ROOT = pathlib.Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("device_tools", ROOT / "tests/video-device.py")
device = importlib.util.module_from_spec(spec)
spec.loader.exec_module(device)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--fps", type=int, choices=range(24, 121), default=120)
    parser.add_argument("--seconds", type=int, choices=range(10, 121), default=30)
    parser.add_argument("--skip-install", action="store_true")
    parser.add_argument("--output", type=pathlib.Path)
    parser.add_argument("--window", type=lambda value: int(value, 0))
    parser.add_argument("--min-fps", type=float)
    parser.add_argument("--max-gap-ms", type=float)
    args = parser.parse_args()
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    output = args.output or ROOT / "build/video-cadence" / run_id
    output.mkdir(parents=True, exist_ok=False)
    previous = device.mappings()
    preferences = device.adb("exec-out", "run-as", device.APP, "cat", "shared_prefs/controller-settings.xml", binary=True)
    (output / "preferences-before.xml").write_bytes(preferences)
    assigned = {"tcp:27184": f"tcp:{device.port()}", "tcp:27183": f"tcp:{device.port()}"}
    while assigned["tcp:27184"] == assigned["tcp:27183"]:
        assigned["tcp:27183"] = f"tcp:{device.port()}"
    pattern = None
    host = None
    try:
        if not args.skip_install:
            for apk in device.apks("profile"):
                print(device.adb("install", "-r", "-t", str(apk)), flush=True)
        if args.window is None:
            pattern = subprocess.Popen([str(ROOT / "build/windows/tests/Release/ift_video_pattern.exe"), "720p"],
                                       stdout=subprocess.PIPE, text=True, creationflags=subprocess.CREATE_NO_WINDOW)
            window = pattern.stdout.readline().strip()
        else:
            window = hex(args.window)
        assert re.fullmatch(r"0x[0-9a-f]+", window), "Video pattern did not start"
        with (output / "host.log").open("w", encoding="utf-8") as log:
            host = subprocess.Popen([str(ROOT / "build/windows/windows/Release/InFalsusTouchHost.exe"),
                                     "--dry-run", "--video", "--no-profile", "--window", window,
                                     "--fps", str(args.fps), "--port", assigned["tcp:27184"].split(":")[1],
                                     "--video-port", assigned["tcp:27183"].split(":")[1]],
                                    stdout=log, stderr=log, creationflags=subprocess.CREATE_NO_WINDOW)
            device.adb("shell", "am", "force-stop", device.APP)
            for local, remote in assigned.items():
                device.adb("reverse", local, remote)
            result = device.adb("shell", "am", "instrument", "-w", "-r", "-e", "videoCadence", "true",
                                "-e", "videoFps", str(args.fps), "-e", "videoSeconds", str(args.seconds),
                                "-e", "class", "dev.cedarflake.ift.VideoCadenceDeviceTest",
                                f"{device.APP}.test/androidx.test.runner.AndroidJUnitRunner", timeout=args.seconds + 90)
            (output / "instrumentation.txt").write_text(result, encoding="utf-8")
            raw = device.adb("exec-out", "run-as", device.APP, "cat", "files/video-cadence.json", binary=True)
            (output / "video-cadence.json").write_bytes(raw)
            assert "OK (1 test)" in result, result
            metrics = json.loads(raw)
            metrics["source"] = "pattern" if args.window is None else "selected-window"
            samples = metrics["samples"][1:]
            metrics.pop("samples")
            for stage in ("receive", "decode", "present"):
                gaps = [sample[stage + "GapMs"] for sample in samples]
                metrics[stage + "MaxGapMs"] = max(gaps)
                metrics[stage + "SecondsWithGapOver50Ms"] = sum(gap > 50 for gap in gaps)
            metrics["worstSecondFps"] = min(sample["presentFps"] for sample in samples)
            metrics["lastLatencyMeanMs"] = samples[-1]["latencyMeanMs"]
            metrics["lastLatencyP95Ms"] = samples[-1]["latencyP95Ms"]
            (output / "summary.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")
            print(json.dumps(metrics, indent=2), flush=True)
            if args.min_fps is not None:
                assert metrics["presentFps"] >= args.min_fps, "Presentation rate below the requested gate"
            if args.max_gap_ms is not None:
                assert metrics["presentMaxGapMs"] <= args.max_gap_ms, "Presentation stall exceeds the requested gate"
    finally:
        try:
            device.adb("shell", "am", "force-stop", device.APP)
            current = device.mappings()
            for local, remote in assigned.items():
                if current.get(local) == remote:
                    if local in previous:
                        device.adb("reverse", local, previous[local])
                    else:
                        device.adb("reverse", "--remove", local)
        finally:
            for process in (host, pattern):
                if process is not None and process.poll() is None:
                    process.terminate()
                    process.wait(timeout=10)
            if pattern is not None:
                (output / "pattern.log").write_text(pattern.stdout.read(), encoding="utf-8")
            after = device.adb("exec-out", "run-as", device.APP, "cat", "shared_prefs/controller-settings.xml", binary=True)
            (output / "preferences-after.xml").write_bytes(after)
            restored = {"preferencesRestored": preferences == after, "mappingsRestored": previous == device.mappings()}
            (output / "restored.json").write_text(json.dumps(restored), encoding="utf-8")
            device.adb("shell", "am", "start", "-n", device.APP + "/dev.cedarflake.ift.MainActivity")
            assert all(restored.values()), restored
    print(f"Evidence: {output.resolve()}")


if __name__ == "__main__":
    main()
