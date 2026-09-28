"""Compare phone presentation policies in one USB video session with dry-run input."""

import argparse
from datetime import datetime, timezone
import importlib.util
import json
import pathlib
import re
import subprocess


ROOT = pathlib.Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("device_tools", ROOT / "tests" / "video-device.py")
device = importlib.util.module_from_spec(spec)
spec.loader.exec_module(device)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", type=pathlib.Path, default=ROOT / "dist/InFalsusTouchHost.exe")
    parser.add_argument("--skip-install", action="store_true")
    parser.add_argument("--probe", choices=("overlays", "surface-hints"), default="overlays")
    parser.add_argument("--fps", type=int, choices=(60, 90, 120), default=60)
    args = parser.parse_args()
    if args.probe == "overlays" and args.fps != 60:
        parser.error("The overlay comparison uses the 60 FPS baseline")
    if args.probe == "overlays":
        probe = "composition"
        phases = ("normal-before", "native-controls", "video-only", "normal-after")
        argument = "videoComposition"
        method = "isolatesVisibleOverlaysWithinOneVideoSession"
    else:
        probe = "surface-hints"
        phases = ("fixed-before", "unrestricted", "fixed-middle", "unspecified", "fixed-after")
        argument = "videoSurfaceHints"
        method = "comparesSurfaceFrameRateHintsWithinOneVideoSession"
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    output = ROOT / f"build/video-{probe}-test" / f"{args.fps}fps-{run_id}"
    serial = device.adb("get-serialno").strip()
    if not serial or serial == "unknown":
        raise RuntimeError("Connect exactly one authorized USB phone")
    output.mkdir(parents=True)
    previous = device.mappings()
    assigned = {"tcp:27184": f"tcp:{device.port()}", "tcp:27183": f"tcp:{device.port()}"}
    while assigned["tcp:27184"] == assigned["tcp:27183"]:
        assigned["tcp:27183"] = f"tcp:{device.port()}"
    pattern = subprocess.Popen([str(ROOT / "build/windows/tests/Release/ift_video_pattern.exe"), "720p"],
                               stdout=subprocess.PIPE, text=True, creationflags=subprocess.CREATE_NO_WINDOW)
    host = None
    try:
        window = pattern.stdout.readline().strip()
        assert re.fullmatch(r"0x[0-9a-f]+", window), "Test window did not start"
        with (output / "host.log").open("w", encoding="utf-8") as log:
            host = subprocess.Popen([str(args.host.resolve()), "--dry-run", "--video", "--window", window,
                                     "--no-profile", "--resolution", "720p", "--fps", str(args.fps),
                                     "--port", assigned["tcp:27184"].split(":")[1],
                                     "--video-port", assigned["tcp:27183"].split(":")[1]],
                                    stdout=log, stderr=log, creationflags=subprocess.CREATE_NO_WINDOW)
            device.adb("shell", "am", "force-stop", device.APP)
            for local, remote in assigned.items():
                device.adb("reverse", local, remote)
            if not args.skip_install:
                print(device.adb("install", "-r", str(ROOT / "dist/InFalsusTouch.apk")), flush=True)
                print(device.adb("install", "-r", "-t", str(ROOT / "android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")), flush=True)
            assert host.poll() is None, "Native video host exited"
            result = device.adb("shell", "am", "instrument", "-w", "-r", "-e", argument, "true",
                                "-e", "videoFps", str(args.fps),
                                "-e", "class", f"dev.cedarflake.ift.VideoCompositionDeviceTest#{method}",
                                f"{device.APP}.test/androidx.test.runner.AndroidJUnitRunner")
            (output / "instrumentation.txt").write_text(result, encoding="utf-8")
            print(result, flush=True)
            for name in (f"video-{probe}.json", *(f"{probe}-{phase}.png" for phase in phases)):
                try:
                    contents = device.adb("exec-out", "run-as", device.APP, "cat", f"files/{name}", binary=True)
                except subprocess.CalledProcessError:
                    continue
                (output / name).write_bytes(contents)
            metrics_path = output / f"video-{probe}.json"
            if metrics_path.exists():
                print(metrics_path.read_text(encoding="utf-8"), flush=True)
            assert "OK (1 test)" in result and "FAILURES!!!" not in result, f"Presentation probe failed; inspect {output}"
            metrics = json.loads(metrics_path.read_text(encoding="utf-8"))
            assert tuple(sample["name"] for sample in metrics["phases"]) == phases
            assert metrics["streamFps"] == args.fps
            print(f"PASS: {args.probe} comparison; evidence: {output}")
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
            (output / "pattern.log").write_text(pattern.stdout.read(), encoding="utf-8")


if __name__ == "__main__":
    main()
