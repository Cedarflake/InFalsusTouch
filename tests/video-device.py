"""Install built APKs, test real USB video, and restore the device's ADB reverse mappings."""

import argparse
from datetime import datetime, timezone
import json
import pathlib
import re
import socket
import subprocess


ROOT = pathlib.Path(__file__).resolve().parent.parent
ADB = ROOT / ".tools" / "android-sdk" / "platform-tools" / "adb.exe"
APP = "dev.cedarflake.infalsustouch"


def adb(*args, binary=False, timeout=150):
    result = subprocess.run([str(ADB), "-d", *args], capture_output=True, check=True,
                            creationflags=subprocess.CREATE_NO_WINDOW, timeout=timeout)
    return result.stdout if binary else result.stdout.decode("utf-8", errors="replace")


def port():
    with socket.socket() as reservation:
        reservation.bind(("127.0.0.1", 0))
        return reservation.getsockname()[1]


def mappings():
    return {parts[1]: parts[2] for line in adb("reverse", "--list").splitlines()
            if len(parts := line.split()) == 3}


def apks(build_mode):
    name = "InFalsusTouch-profile.apk" if build_mode == "profile" else "InFalsusTouch.apk"
    return ROOT / "dist" / name, ROOT / f"android/app/build/outputs/apk/androidTest/{build_mode}/app-{build_mode}-androidTest.apk"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", type=pathlib.Path, default=ROOT / "dist/InFalsusTouchHost.exe")
    parser.add_argument("--resolution", choices=("720p", "1080p"), default="720p")
    parser.add_argument("--fps", type=int, choices=range(24, 121), default=60)
    parser.add_argument("--seconds", type=int, choices=range(6, 61), default=10)
    parser.add_argument("--skip-install", action="store_true")
    parser.add_argument("--build-mode", choices=("debug", "profile"), default="debug")
    parser.add_argument("--output", type=pathlib.Path)
    parser.add_argument("--lifecycle-cycles", type=int, choices=range(1, 11))
    args = parser.parse_args()
    if args.lifecycle_cycles and (args.fps != 60 or args.resolution != "720p"):
        parser.error("Lifecycle acceptance currently uses the 720p60 baseline")
    width, height = (1920, 1080) if args.resolution == "1080p" else (1280, 720)
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    probe = "lifecycle-" if args.lifecycle_cycles else ""
    output = args.output or ROOT / "build" / "video-device-test" / f"{probe}{args.resolution}{args.fps}-{args.build_mode}-{run_id}"
    serial = adb("get-serialno").strip()
    if not serial or serial == "unknown":
        raise RuntimeError("Connect exactly one authorized USB phone")
    output.mkdir(parents=True, exist_ok=True)
    previous = mappings()
    assigned = {"tcp:27184": f"tcp:{port()}", "tcp:27183": f"tcp:{port()}"}
    while assigned["tcp:27184"] == assigned["tcp:27183"]:
        assigned["tcp:27183"] = f"tcp:{port()}"
    pattern = subprocess.Popen([str(ROOT / "build/windows/tests/Release/ift_video_pattern.exe"), args.resolution],
                               stdout=subprocess.PIPE, text=True, creationflags=subprocess.CREATE_NO_WINDOW)
    host = None
    preferences = None
    try:
        window = pattern.stdout.readline().strip()
        assert re.fullmatch(r"0x[0-9a-f]+", window), "Test window did not start"
        with (output / "host.log").open("w", encoding="utf-8") as log:
            host = subprocess.Popen([str(args.host.resolve()), "--dry-run", "--video", "--window", window,
                                     "--no-profile", "--resolution", args.resolution, "--fps", str(args.fps),
                                     "--port", assigned["tcp:27184"].split(":")[1],
                                     "--video-port", assigned["tcp:27183"].split(":")[1],
                                     "--trace", str(output / "input-trace.txt")],
                                    stdout=log, stderr=log, creationflags=subprocess.CREATE_NO_WINDOW)
            for local, remote in assigned.items():
                adb("reverse", local, remote)
            if not args.skip_install:
                app_apk, test_apk = apks(args.build_mode)
                print(adb("install", "-r", str(app_apk)), flush=True)
                print(adb("install", "-r", "-t", str(test_apk)), flush=True)
            assert host.poll() is None, "Native video host exited"
            if args.lifecycle_cycles:
                preferences = adb("exec-out", "run-as", APP, "cat", "shared_prefs/controller-settings.xml", binary=True)
                (output / "preferences-before.xml").write_bytes(preferences)
            test_class = "VideoLifecycleDeviceTest" if args.lifecycle_cycles else "VideoDeviceTest"
            metrics_name = "video-lifecycle-metrics.json" if args.lifecycle_cycles else "video-metrics.json"
            result = adb("shell", "am", "instrument", "-w", "-r", "-e",
                         "usbVideoLifecycle" if args.lifecycle_cycles else "usbVideo", "true",
                         "-e", "appBuildMode", args.build_mode,
                         "-e", "videoWidth", str(width), "-e", "videoHeight", str(height),
                         "-e", "videoFps", str(args.fps),
                         "-e", "videoSeconds", str(args.seconds),
                         "-e", "lifecycleCycles", str(args.lifecycle_cycles or 3), "-e", "class",
                         f"dev.cedarflake.ift.{test_class}", f"{APP}.test/androidx.test.runner.AndroidJUnitRunner",
                         timeout=60 + args.lifecycle_cycles * 100 if args.lifecycle_cycles else 150)
            (output / "instrumentation.txt").write_text(result, encoding="utf-8")
            print(result, flush=True)
            metrics = None
            try:
                metrics_bytes = adb("exec-out", "run-as", APP, "cat", f"files/{metrics_name}", binary=True)
            except subprocess.CalledProcessError:
                pass
            else:
                try:
                    metrics = json.loads(metrics_bytes)
                except (json.JSONDecodeError, UnicodeDecodeError):
                    (output / "metrics-read.txt").write_bytes(metrics_bytes)
                else:
                    (output / metrics_name).write_bytes(metrics_bytes)
                    print(json.dumps(metrics, indent=2), flush=True)
            if "OK (1 test)" not in result or "FAILURES!!!" in result:
                print((output / "host.log").read_text(encoding="utf-8", errors="replace"))
                raise RuntimeError("USB video device test failed")
            if not args.lifecycle_cycles:
                for name in ("video-surface.png", "video-screen.png", "calibration-screen.png"):
                    (output / name).write_bytes(adb("exec-out", "run-as", APP, "cat", f"files/{name}", binary=True))
            assert metrics is not None, "Video instrumentation did not write its measurements"
            assert (metrics["width"], metrics["height"]) == (width, height)
            assert metrics["targetFps"] == args.fps
            assert metrics["appBuildMode"] == args.build_mode
            batches = 1
            if args.lifecycle_cycles:
                assert metrics["passed"] and metrics["cycles"] == args.lifecycle_cycles
                phases = metrics["phases"]
                assert len(phases) == args.lifecycle_cycles * 3 and all(phase["completed"] for phase in phases)
                batches = args.lifecycle_cycles * 3 + 1
                assert metrics["holdBatches"] == batches
            trace = (output / "input-trace.txt").read_text(encoding="utf-8")
            for lane in range(1, 7):
                events = [line for line in trace.splitlines() if line in (f"DOWN {lane}", f"UP {lane}")]
                assert events == [f"DOWN {lane}", f"UP {lane}"] * batches, f"Lane {lane} hold/release mismatch"
            movements = [int(line.split()[1]) for line in trace.splitlines() if line.startswith("REL ")]
            expected = ([metrics["touchWidth"] * 0.125] * batches if args.lifecycle_cycles else
                        [metrics["touchWidth"] * 0.25, -metrics["touchWidth"] * 0.125])
            assert len(movements) == len(expected) and all(abs(actual - value) <= 1 for actual, value in zip(movements, expected)), "Pixel Field movement mismatch during video playback"
            assert "ABS " not in trace, "Relative Field unexpectedly used absolute input"
            if args.lifecycle_cycles:
                print(f"PASS: {len(phases)} streaming lifecycle transitions, {batches} six-key holds/releases, no stale gesture replay")
            else:
                print("PASS: real USB H.264 decode, six color checks, seven pointers, release and video reconnect")
            print(f"Evidence: {output.resolve()}")
    finally:
        try:
            adb("shell", "am", "force-stop", APP)
            current = mappings()
            for local, remote in assigned.items():
                if current.get(local) == remote:
                    if local in previous:
                        adb("reverse", local, previous[local])
                    else:
                        adb("reverse", "--remove", local)
        finally:
            for process in (host, pattern):
                if process is not None and process.poll() is None:
                    process.terminate()
                    process.wait(timeout=10)
            (output / "pattern.log").write_text(pattern.stdout.read(), encoding="utf-8")
            if preferences is not None:
                try:
                    after = adb("exec-out", "run-as", APP, "cat", "shared_prefs/controller-settings.xml", binary=True)
                    (output / "preferences-after.xml").write_bytes(after)
                    restored = {"preferencesRestored": preferences == after, "mappingsRestored": previous == mappings()}
                    (output / "restored.json").write_text(json.dumps(restored, indent=2), encoding="utf-8")
                    assert all(restored.values()), f"Phone state was not restored: {restored}"
                finally:
                    adb("shell", "am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER",
                        "-n", APP + "/dev.cedarflake.ift.MainActivity")


if __name__ == "__main__":
    main()
