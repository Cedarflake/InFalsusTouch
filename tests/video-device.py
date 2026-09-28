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


def adb(*args, binary=False):
    result = subprocess.run([str(ADB), "-d", *args], capture_output=True, check=True,
                            creationflags=subprocess.CREATE_NO_WINDOW, timeout=150)
    return result.stdout if binary else result.stdout.decode("utf-8", errors="replace")


def port():
    with socket.socket() as reservation:
        reservation.bind(("127.0.0.1", 0))
        return reservation.getsockname()[1]


def mappings():
    return {parts[1]: parts[2] for line in adb("reverse", "--list").splitlines()
            if len(parts := line.split()) == 3}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--resolution", choices=("720p", "1080p"), default="720p")
    parser.add_argument("--seconds", type=int, choices=range(6, 61), default=10)
    parser.add_argument("--skip-install", action="store_true")
    parser.add_argument("--output", type=pathlib.Path)
    args = parser.parse_args()
    width, height = (1920, 1080) if args.resolution == "1080p" else (1280, 720)
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    output = args.output or ROOT / "build" / "video-device-test" / f"{args.resolution}-{run_id}"
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
    try:
        window = pattern.stdout.readline().strip()
        assert re.fullmatch(r"0x[0-9a-f]+", window), "Test window did not start"
        with (output / "host.log").open("w", encoding="utf-8") as log:
            host = subprocess.Popen([str(ROOT / "dist/InFalsusTouchHost.exe"), "--dry-run", "--video", "--window", window,
                                     "--no-profile", "--resolution", args.resolution,
                                     "--port", assigned["tcp:27184"].split(":")[1],
                                     "--video-port", assigned["tcp:27183"].split(":")[1],
                                     "--trace", str(output / "input-trace.txt")],
                                    stdout=log, stderr=log, creationflags=subprocess.CREATE_NO_WINDOW)
            for local, remote in assigned.items():
                adb("reverse", local, remote)
            if not args.skip_install:
                print(adb("install", "-r", str(ROOT / "dist/InFalsusTouch.apk")), flush=True)
                print(adb("install", "-r", "-t", str(ROOT / "android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")), flush=True)
            assert host.poll() is None, "Native video host exited"
            result = adb("shell", "am", "instrument", "-w", "-r", "-e", "usbVideo", "true",
                         "-e", "videoWidth", str(width), "-e", "videoHeight", str(height),
                         "-e", "videoSeconds", str(args.seconds), "-e", "class",
                         "dev.cedarflake.ift.VideoDeviceTest", f"{APP}.test/androidx.test.runner.AndroidJUnitRunner")
            (output / "instrumentation.txt").write_text(result, encoding="utf-8")
            print(result, flush=True)
            if "OK (1 test)" not in result or "FAILURES!!!" in result:
                print((output / "host.log").read_text(encoding="utf-8", errors="replace"))
                raise RuntimeError("USB video device test failed")
            for name in ("video-surface.png", "video-screen.png", "calibration-screen.png", "video-metrics.json"):
                (output / name).write_bytes(adb("exec-out", "run-as", APP, "cat", f"files/{name}", binary=True))
            metrics = json.loads((output / "video-metrics.json").read_text())
            assert (metrics["width"], metrics["height"]) == (width, height)
            print(json.dumps(metrics, indent=2))
            trace = (output / "input-trace.txt").read_text(encoding="utf-8")
            for lane in range(1, 7):
                assert trace.count(f"DOWN {lane}\n") == trace.count(f"UP {lane}\n") == 1, f"Lane {lane} hold/release mismatch"
            movements = [int(line.split()[1]) for line in trace.splitlines() if line.startswith("REL ")]
            expected = [metrics["touchWidth"] * 0.25, -metrics["touchWidth"] * 0.125]
            assert len(movements) == 2 and all(abs(actual - value) <= 1 for actual, value in zip(movements, expected)), "Pixel Field movement mismatch during video playback"
            assert "ABS " not in trace, "Relative Field unexpectedly used absolute input"
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


if __name__ == "__main__":
    main()
