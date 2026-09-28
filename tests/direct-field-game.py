"""Verify direct Field targets in an already-running foreground In Falsus chart."""

import argparse
import ctypes
from ctypes import wintypes
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
import math
from pathlib import Path
import socket
import struct
import subprocess
import time


ROOT = Path(__file__).resolve().parent.parent
BINARY_SHA256 = "ab1d8fa7739078510fab5f8580095c90ea0f5e9236ac9b918e934cb9ae1b7d9c"
spec = importlib.util.spec_from_file_location("wire", ROOT / "tests/tcp-integration.py")
wire = importlib.util.module_from_spec(spec)
spec.loader.exec_module(wire)


class ModuleEntry(ctypes.Structure):
    _fields_ = [("size", wintypes.DWORD), ("moduleId", wintypes.DWORD), ("processId", wintypes.DWORD),
                ("globalUsage", wintypes.DWORD), ("processUsage", wintypes.DWORD),
                ("base", ctypes.c_void_p), ("baseSize", wintypes.DWORD), ("module", wintypes.HMODULE),
                ("name", wintypes.WCHAR * 256), ("path", wintypes.WCHAR * 260)]


def main(window, positions, rate, seconds, host_path):
    user = ctypes.WinDLL("user32", use_last_error=True)
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    user.SetProcessDpiAwarenessContext.argtypes = [ctypes.c_void_p]
    user.SetProcessDpiAwarenessContext(ctypes.c_void_p(-4))
    user.GetForegroundWindow.restype = wintypes.HWND
    user.GetWindowThreadProcessId.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.DWORD)]
    user.GetClipCursor.argtypes = [ctypes.POINTER(wintypes.RECT)]
    kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
    kernel.OpenProcess.restype = wintypes.HANDLE
    kernel.ReadProcessMemory.argtypes = [wintypes.HANDLE, ctypes.c_void_p, ctypes.c_void_p,
                                        ctypes.c_size_t, ctypes.POINTER(ctypes.c_size_t)]
    kernel.CloseHandle.argtypes = [wintypes.HANDLE]
    kernel.CreateToolhelp32Snapshot.argtypes = [wintypes.DWORD, wintypes.DWORD]
    kernel.CreateToolhelp32Snapshot.restype = wintypes.HANDLE
    kernel.Module32FirstW.argtypes = [wintypes.HANDLE, ctypes.POINTER(ModuleEntry)]
    kernel.Module32NextW.argtypes = [wintypes.HANDLE, ctypes.POINTER(ModuleEntry)]
    pid = wintypes.DWORD()
    user.GetWindowThreadProcessId(window, ctypes.byref(pid))
    process = kernel.OpenProcess(0x1010, False, pid.value)
    if not process:
        raise ctypes.WinError(ctypes.get_last_error())
    module = None
    try:
        modules = kernel.CreateToolhelp32Snapshot(8, pid.value)
        if modules == ctypes.c_void_p(-1).value:
            raise ctypes.WinError(ctypes.get_last_error())
        try:
            entry = ModuleEntry(size=ctypes.sizeof(ModuleEntry))
            valid = kernel.Module32FirstW(modules, ctypes.byref(entry))
            while valid:
                if entry.name.lower() == "gameassembly.dll":
                    module = (entry.base, Path(entry.path))
                    break
                valid = kernel.Module32NextW(modules, ctypes.byref(entry))
        finally:
            kernel.CloseHandle(modules)
        if module is None or hashlib.sha256(module[1].read_bytes()).hexdigest() != BINARY_SHA256:
            raise RuntimeError("Unsupported game binary; no input sent")

        def read(address, fmt):
            buffer = ctypes.create_string_buffer(struct.calcsize(fmt))
            count = ctypes.c_size_t()
            if not kernel.ReadProcessMemory(process, address, buffer, len(buffer), ctypes.byref(count)) or count.value != len(buffer):
                raise ctypes.WinError(ctypes.get_last_error())
            return struct.unpack(fmt, buffer.raw)[0]

        def feedback():
            scene = read(module[0] + 0x319E300, "<Q")
            statics = read(scene + 0xB8, "<Q")
            field = read(statics + 0x1B8, "<Q")
            return {"identity": hex(field), "position": read(field + 0x10, "<f"),
                    "visible": read(field + 0x98, "<f"), "sensitivity": read(field + 0xB8, "<d")}

        def guard():
            clip = wintypes.RECT()
            if user.GetForegroundWindow() != window or not user.GetClipCursor(ctypes.byref(clip)):
                raise RuntimeError("Game is not foreground; no further input sent")
            if clip.right - clip.left > 2 or clip.bottom - clip.top > 2:
                raise RuntimeError(f"An active chart with a locked cursor is required: {(clip.left, clip.top, clip.right, clip.bottom)}")

        guard()
        output = ROOT / "build/game-input-test"
        output.mkdir(parents=True, exist_ok=True)
        artifact = output / ("direct-field-" + datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ"))
        metrics = {"binarySha256": BINARY_SHA256, "displayWidth": user.GetSystemMetrics(0),
                   "samples": [], "sweep": [], "evidence": "Game-state readback, not screen or physical touch latency"}
        with socket.socket() as reservation:
            reservation.bind(("127.0.0.1", 0))
            port = reservation.getsockname()[1]
        with artifact.with_suffix(".log").open("w", encoding="utf-8") as log:
            host = subprocess.Popen([str(host_path), "--no-profile", "--no-video", "--window", hex(window),
                                     "--port", str(port)], stdout=log, stderr=log,
                                    creationflags=subprocess.CREATE_NO_WINDOW)
            connection = None
            try:
                deadline = time.monotonic() + 5
                while connection is None:
                    try:
                        connection = wire.connect(port)
                    except ConnectionRefusedError:
                        if host.poll() is not None or time.monotonic() >= deadline:
                            raise RuntimeError("Test Host did not start")
                        time.sleep(0.02)
                sequence = 3

                def send(kind, value=0):
                    nonlocal sequence
                    guard()
                    reply = wire.exchange(connection, kind, sequence, value=value)
                    sequence += 1
                    if kind != 9 and reply[4] != 4:
                        raise RuntimeError(f"Field ownership not granted: {reply[4]}")

                def settle(target, started):
                    deadline = started + 0.1
                    while True:
                        guard()
                        state = feedback()
                        tolerance = max(0.000002, state["sensitivity"] / metrics["displayWidth"] * 0.6)
                        if abs(state["position"] - target) <= tolerance:
                            return {"target": target, "after": state,
                                    "readbackMs": (time.monotonic() - started) * 1000}
                        if time.monotonic() > deadline:
                            raise RuntimeError(f"Field did not reach {target}: {state}")
                        time.sleep(0.001)

                for target in positions:
                    send(8)
                    before = feedback()
                    started = time.monotonic()
                    send(4, target)
                    send(9)
                    sample = settle(target, started)
                    sample["before"] = before
                    metrics["samples"].append(sample)
                    print(json.dumps(sample), flush=True)
                if seconds:
                    send(8)
                    started = time.monotonic()
                    for index in range(max(1, round(rate * seconds))):
                        due = started + index / rate
                        time.sleep(max(0, due - time.monotonic()))
                        target = 0.5 + 0.4 * math.sin(2 * math.pi * index / rate)
                        send(4, target)
                        state = feedback()
                        if not -0.005 <= state["position"] <= 1.005:
                            raise RuntimeError(f"Continuous touch overshot Field: {state}")
                        metrics["sweep"].append({"target": target, "state": state,
                                                 "elapsedMs": (time.monotonic() - started) * 1000})
                    metrics["maxTrackingError"] = max(abs(item["target"] - item["state"]["position"])
                                                       for item in metrics["sweep"])
                    if metrics["maxTrackingError"] > 0.1:
                        raise RuntimeError(f"Continuous touch did not follow targets: {metrics['maxTrackingError']}")
                    started = time.monotonic()
                    send(4, 0.5)
                    send(9)
                    metrics["final"] = settle(0.5, started)
                metrics["passed"] = True
            except Exception as error:
                metrics["error"] = str(error)
                raise
            finally:
                if connection is not None:
                    connection.close()
                if host.poll() is None:
                    host.terminate()
                    host.wait(timeout=5)
                artifact.with_suffix(".json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")
                print(f"Evidence: {artifact.with_suffix('.json')}", flush=True)
    finally:
        kernel.CloseHandle(process)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--window", type=lambda value: int(value, 0), required=True)
    parser.add_argument("--positions", nargs="+", type=float, default=[0.25, 0.5, 0.75, 0, 1, 0.25, 0.5])
    parser.add_argument("--rate", type=int, choices=[60, 120], default=120)
    parser.add_argument("--seconds", type=float, default=2)
    parser.add_argument("--host", type=Path, default=ROOT / "dist/InFalsusTouchHost.exe")
    args = parser.parse_args()
    if not all(math.isfinite(x) and 0 <= x <= 1 for x in args.positions) or not 0 <= args.seconds <= 10:
        parser.error("Positions must be in [0, 1], duration must be in [0, 10]")
    main(args.window, args.positions, args.rate, args.seconds, args.host.resolve())
