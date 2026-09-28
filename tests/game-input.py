"""Send selected USB controller lanes to an explicitly selected foreground game."""

import argparse
import ctypes
from ctypes import wintypes
import importlib.util
import pathlib
import subprocess
import time


ROOT = pathlib.Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "build" / "game-input-test"
spec = importlib.util.spec_from_file_location("device_tools", ROOT / "tests" / "video-device.py")
device = importlib.util.module_from_spec(spec)
spec.loader.exec_module(device)


def main(window, lanes):
    label = "-".join(str(lane) for lane in lanes)
    user32 = ctypes.WinDLL("user32")
    user32.GetWindowThreadProcessId.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.DWORD)]
    user32.GetWindowThreadProcessId.restype = wintypes.DWORD
    user32.GetKeyboardLayout.argtypes = [wintypes.DWORD]
    user32.GetKeyboardLayout.restype = ctypes.c_void_p
    thread = user32.GetWindowThreadProcessId(window, None)
    if not thread or user32.GetKeyboardLayout(thread) & 0xFFFF != 0x0409:
        raise RuntimeError("Select the US English keyboard layout in the target game first")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    previous = device.mappings()
    remote = f"tcp:{device.port()}"
    host = None
    try:
        with (OUTPUT / f"host-lane-{label}.log").open("w", encoding="utf-8") as log:
            host = subprocess.Popen([str(ROOT / "dist/InFalsusTouchHost.exe"), "--no-profile", "--no-video",
                                     "--window", hex(window), "--port", remote.split(":")[1]],
                                    stdout=log, stderr=log, creationflags=subprocess.CREATE_NO_WINDOW)
            device.adb("reverse", "tcp:27184", remote)
            result = device.adb("shell", "am", "instrument", "-w", "-r", "-e", "gameInput", "true",
                                "-e", "lane", ",".join(str(lane) for lane in lanes), "-e", "class", "dev.cedarflake.ift.GameInputDeviceTest",
                                f"{device.APP}.test/androidx.test.runner.AndroidJUnitRunner")
            (OUTPUT / f"lane-{label}.txt").write_text(result, encoding="utf-8")
            print(result, flush=True)
            if "OK (1 test)" not in result or "FAILURES!!!" in result:
                raise RuntimeError("Single-lane USB game input test failed")
    finally:
        try:
            device.adb("shell", "am", "force-stop", device.APP)
            # Leave Host alive long enough to release input after EOF or its watchdog.
            time.sleep(0.6)
            if device.mappings().get("tcp:27184") == remote:
                if "tcp:27184" in previous:
                    device.adb("reverse", "tcp:27184", previous["tcp:27184"])
                else:
                    device.adb("reverse", "--remove", "tcp:27184")
        finally:
            if host is not None and host.poll() is None:
                host.terminate()
                host.wait(timeout=10)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--window", type=lambda value: int(value, 0), required=True)
    parser.add_argument("--lane", type=int, choices=range(1, 7), nargs="+", required=True)
    args = parser.parse_args()
    main(args.window, args.lane)
