"""Verify a selected real game window through USB video; all input remains dry-run."""

import argparse
import importlib.util
import json
import pathlib
import subprocess


ROOT = pathlib.Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("device_tools", ROOT / "tests" / "video-device.py")
device = importlib.util.module_from_spec(spec)
spec.loader.exec_module(device)


def main(window, system_refresh, skip_install, output, host_path, fps):
    output = output or ROOT / "build" / ("game-video-system-refresh-test" if system_refresh else "game-video-test")
    serial = device.adb("get-serialno").strip()
    if not serial or serial == "unknown":
        raise RuntimeError("Connect exactly one authorized USB phone")
    output.mkdir(parents=True, exist_ok=True)
    previous = device.mappings()
    assigned = {"tcp:27184": f"tcp:{device.port()}", "tcp:27183": f"tcp:{device.port()}"}
    while assigned["tcp:27184"] == assigned["tcp:27183"]:
        assigned["tcp:27183"] = f"tcp:{device.port()}"
    host = None
    try:
        with (output / "host.log").open("w", encoding="utf-8") as log:
            host = subprocess.Popen([str(host_path.resolve()), "--no-profile", "--dry-run", "--video", "--window", hex(window),
                                     "--fps", str(fps),
                                     "--port", assigned["tcp:27184"].split(":")[1],
                                     "--video-port", assigned["tcp:27183"].split(":")[1],
                                     "--trace", str(output / "input-trace.txt")],
                                    stdout=log, stderr=log, creationflags=subprocess.CREATE_NO_WINDOW)
            for local, remote in assigned.items():
                device.adb("reverse", local, remote)
            device.adb("shell", "am", "force-stop", device.APP)
            if not skip_install:
                print(device.adb("install", "-r", str(ROOT / "dist/InFalsusTouch.apk")), flush=True)
                print(device.adb("install", "-r", "-t", str(ROOT / "android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")), flush=True)
            result = device.adb("shell", "am", "instrument", "-w", "-r", "-e", "gameVideo", "true",
                                "-e", "videoFps", str(fps),
                                "-e", "highRefresh", "false" if system_refresh else "true", "-e", "class",
                                "dev.cedarflake.ift.GameVideoDeviceTest", f"{device.APP}.test/androidx.test.runner.AndroidJUnitRunner")
            (output / "instrumentation.txt").write_text(result, encoding="utf-8")
            print(result, flush=True)
            if "OK (1 test)" not in result or "FAILURES!!!" in result:
                raise RuntimeError(f"Selected-game USB video test failed; inspect {output}")
            for name in ("game-surface.png", "game-phone.png", "game-phone-pressed.png", "game-metrics.json"):
                (output / name).write_bytes(device.adb("exec-out", "run-as", device.APP, "cat", f"files/{name}", binary=True))
            metrics = json.loads((output / "game-metrics.json").read_text())
            assert metrics["targetFps"] == fps
            print(json.dumps(metrics, indent=2))
            trace = (output / "input-trace.txt").read_text(encoding="utf-8")
            for lane in range(1, 7):
                assert trace.count(f"DOWN {lane}\n") == trace.count(f"UP {lane}\n") == 1, f"Aligned lane {lane} hold/release mismatch"
            movements = [int(line.split()[1]) for line in trace.splitlines() if line.startswith("REL ")]
            assert len(movements) == 1 and abs(movements[0] - metrics["layout"]["width"] * 0.215) <= 1, "Aligned pixel Field movement mismatch"
            assert "ABS " not in trace, "Relative Field unexpectedly used absolute input"
            print("PASS: centered game video, aligned seven-pointer transport and local feedback capture")
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
            if host is not None and host.poll() is None:
                host.terminate()
                host.wait(timeout=10)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", type=pathlib.Path, default=ROOT / "dist/InFalsusTouchHost.exe")
    parser.add_argument("--fps", type=int, choices=range(24, 121), default=60)
    parser.add_argument("--window", type=lambda value: int(value, 0), required=True)
    parser.add_argument("--system-refresh", action="store_true")
    parser.add_argument("--skip-install", action="store_true")
    parser.add_argument("--output", type=pathlib.Path)
    args = parser.parse_args()
    main(args.window, args.system_refresh, args.skip_install, args.output, args.host, args.fps)
