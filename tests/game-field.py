"""Probe Host Field injection in a selected foreground game's tutorial."""

import argparse
import ctypes
from ctypes import wintypes
import importlib.util
import json
import pathlib
import socket
import subprocess
import time


ROOT = pathlib.Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("wire", ROOT / "tests/tcp-integration.py")
wire = importlib.util.module_from_spec(spec)
spec.loader.exec_module(wire)


def main(window, position, relative, repeat):
    user32 = ctypes.WinDLL("user32")
    user32.SetProcessDpiAwarenessContext.argtypes = [ctypes.c_void_p]
    assert user32.SetProcessDpiAwarenessContext(ctypes.c_void_p(-4))
    user32.GetForegroundWindow.restype = wintypes.HWND
    user32.GetCursorPos.argtypes = [ctypes.POINTER(wintypes.POINT)]
    assert user32.GetForegroundWindow() == window, "Focus the selected game first"
    output = ROOT / "build/game-input-test"
    output.mkdir(parents=True, exist_ok=True)
    with socket.socket() as reservation:
        reservation.bind(("127.0.0.1", 0))
        port = reservation.getsockname()[1]
    with (output / "host-field.log").open("w", encoding="utf-8") as log:
        host = subprocess.Popen([str(ROOT / "dist/InFalsusTouchHost.exe"), "--no-profile", "--no-video",
                                 "--window", hex(window), "--port", str(port)], stdout=log, stderr=log,
                                creationflags=subprocess.CREATE_NO_WINDOW)
        connection = None
        try:
            deadline = time.monotonic() + 5
            while connection is None:
                try:
                    connection = wire.connect(port)
                except ConnectionRefusedError:
                    assert host.poll() is None and time.monotonic() < deadline, "Host did not start"
                    time.sleep(0.05)
            before = wintypes.POINT()
            assert user32.GetCursorPos(ctypes.byref(before))
            for step in range(repeat):
                time.sleep(0.06)
                assert wire.exchange(connection, 5 if relative else 4, 3 + step, value=position)[4] == 0
            time.sleep(0.2)
            after = wintypes.POINT()
            assert user32.GetCursorPos(ctypes.byref(after))
            mode = "relative" if relative else "absolute"
            metrics = {"mode": mode, "value": position, "repeat": repeat,
                       "cursorBefore": [before.x, before.y], "cursorAfter": [after.x, after.y]}
            (output / f"field-{mode}-{position}-{repeat}.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")
            print(json.dumps(metrics))
        finally:
            if connection is not None:
                connection.close()
            if host.poll() is None:
                host.terminate()
                host.wait(timeout=5)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--window", type=lambda value: int(value, 0), required=True)
    values = parser.add_mutually_exclusive_group(required=True)
    values.add_argument("--position", type=float)
    values.add_argument("--delta", type=float)
    parser.add_argument("--repeat", type=int, choices=range(1, 11), default=1)
    args = parser.parse_args()
    relative = args.delta is not None
    value = args.delta if relative else args.position
    if not (-1 if relative else 0) <= value <= 1:
        parser.error("Position must be within [0, 1]; relative delta must be within [-1, 1]")
    main(args.window, value, relative, args.repeat)
