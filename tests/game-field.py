"""Probe Host Field injection in a selected foreground game's active chart."""

import argparse
import ctypes
from ctypes import wintypes
from datetime import datetime, timezone
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


def main(window, values, relative, repeat, allow_unlocked):
    user32 = ctypes.WinDLL("user32", use_last_error=True)
    user32.SetProcessDpiAwarenessContext.argtypes = [ctypes.c_void_p]
    if not user32.SetProcessDpiAwarenessContext(ctypes.c_void_p(-4)):
        raise ctypes.WinError(ctypes.get_last_error())
    user32.GetForegroundWindow.restype = wintypes.HWND
    user32.GetCursorPos.argtypes = [ctypes.POINTER(wintypes.POINT)]
    user32.GetClipCursor.argtypes = [ctypes.POINTER(wintypes.RECT)]
    user32.GetClientRect.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.RECT)]
    user32.ClientToScreen.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.POINT)]
    user32.GetDpiForWindow.argtypes = [wintypes.HWND]
    user32.GetDpiForWindow.restype = wintypes.UINT
    user32.GetWindowThreadProcessId.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.DWORD)]
    user32.GetWindowThreadProcessId.restype = wintypes.DWORD
    user32.GetKeyboardLayout.argtypes = [wintypes.DWORD]
    user32.GetKeyboardLayout.restype = ctypes.c_void_p

    def cursor_snapshot():
        point = wintypes.POINT()
        clip = wintypes.RECT()
        if not user32.GetCursorPos(ctypes.byref(point)) or not user32.GetClipCursor(ctypes.byref(clip)):
            raise ctypes.WinError(ctypes.get_last_error())
        return {"cursor": [point.x, point.y],
                "clipRect": [clip.left, clip.top, clip.right, clip.bottom]}

    def client_snapshot():
        bounds = wintypes.RECT()
        origin = wintypes.POINT()
        if not user32.GetClientRect(window, ctypes.byref(bounds)) or not user32.ClientToScreen(window, ctypes.byref(origin)):
            raise ctypes.WinError(ctypes.get_last_error())
        return [origin.x, origin.y, bounds.right, bounds.bottom]

    def check_gameplay():
        if user32.GetForegroundWindow() != window:
            raise RuntimeError("Game is not foreground; probe stopped")
        if not allow_unlocked:
            left, top, right, bottom = cursor_snapshot()["clipRect"]
            x, y, width, height = client_snapshot()
            is_locked = (0 <= right - left <= 2 and 0 <= bottom - top <= 2 and
                         x <= left < x + width and y <= top < y + height)
            if not is_locked:
                raise RuntimeError("Game cursor is not locked; probe stopped. Start a chart before probing.")

    check_gameplay()
    output = ROOT / "build/game-input-test"
    output.mkdir(parents=True, exist_ok=True)
    mode = "relative" if relative else "absolute"
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    artifact = output / f"field-{mode}-{run_id}"
    thread = user32.GetWindowThreadProcessId(window, None)
    metrics = {"window": hex(window), "mode": mode, "values": values, "repeat": repeat,
               "requireLockedCursor": not allow_unlocked,
               "dpi": user32.GetDpiForWindow(window), "client": client_snapshot(),
               "keyboardLayout": hex(user32.GetKeyboardLayout(thread)),
               "evidence": "OS cursor and Host ACKs only; game Field position must be inspected separately.",
               "observations": []}
    with socket.socket() as reservation:
        reservation.bind(("127.0.0.1", 0))
        port = reservation.getsockname()[1]
    with artifact.with_suffix(".log").open("w", encoding="utf-8") as log:
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
                    if host.poll() is not None or time.monotonic() >= deadline:
                        raise RuntimeError("Host did not start")
                    time.sleep(0.05)
            sequence = 3

            def send(kind, value=0.0):
                nonlocal sequence
                check_gameplay()
                status = wire.exchange(connection, kind, sequence, value=value)[4]
                sequence += 1
                if status != 4:
                    raise RuntimeError(f"Host did not grant Field ownership: {status}")
                return status

            send(8)
            for position in values * repeat:
                time.sleep(0.06)
                observation = {"value": position, "client": client_snapshot(),
                               "before": cursor_snapshot(), "samples": []}
                metrics["observations"].append(observation)
                started = time.monotonic()
                observation["ackStatus"] = send(5 if relative else 4, position)
                for delay in (0, 0.01, 0.05, 0.2):
                    time.sleep(max(0, started + delay - time.monotonic()))
                    check_gameplay()
                    sample = cursor_snapshot()
                    sample["elapsedMs"] = (time.monotonic() - started) * 1000
                    observation["samples"].append(sample)
                print(json.dumps({"value": position, "ackStatus": observation["ackStatus"],
                                  "client": observation["client"],
                                  "before": observation["before"],
                                  "after": observation["samples"][-1]}), flush=True)
            metrics["transportCompleted"] = True
        except Exception as error:
            metrics["error"] = str(error)
            raise
        finally:
            if connection is not None:
                connection.close()
                time.sleep(0.1)
            if host.poll() is None:
                host.terminate()
                host.wait(timeout=5)
            path = artifact.with_suffix(".json")
            path.write_text(json.dumps(metrics, indent=2), encoding="utf-8")
            print(f"Probe evidence: {path}", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--window", type=lambda value: int(value, 0), required=True)
    values = parser.add_mutually_exclusive_group(required=True)
    values.add_argument("--position", type=float, nargs="+", help="Ordered absolute positions in [0, 1]")
    values.add_argument("--delta", type=float, nargs="+", help="Ordered relative deltas in [-1, 1]")
    parser.add_argument("--repeat", type=int, choices=range(1, 11), default=1)
    parser.add_argument("--allow-unlocked", action="store_true", help="Allow menu/OS cursor diagnostics")
    args = parser.parse_args()
    relative = args.delta is not None
    positions = args.delta if relative else args.position
    if not all((-1 if relative else 0) <= value <= 1 for value in positions):
        parser.error("Position must be within [0, 1]; relative delta must be within [-1, 1]")
    main(args.window, positions, relative, args.repeat, args.allow_unlocked)
