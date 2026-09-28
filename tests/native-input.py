"""Exercise real SendInput against a project-owned window; optional foreground-loss step."""

import argparse
import ctypes
from ctypes import wintypes
import importlib.util
import json
import pathlib
import socket
import subprocess
import threading
import time


ROOT = pathlib.Path(__file__).resolve().parent.parent
OUTPUT = ROOT / "build" / "native-input-test"
spec = importlib.util.spec_from_file_location("protocol_test", ROOT / "tests" / "tcp-integration.py")
wire = importlib.util.module_from_spec(spec)
spec.loader.exec_module(wire)
user32 = ctypes.WinDLL("user32", use_last_error=True)
user32.SetProcessDpiAwarenessContext.argtypes = [ctypes.c_void_p]
assert user32.SetProcessDpiAwarenessContext(ctypes.c_void_p(-4)), "Test driver must use physical per-monitor coordinates"
user32.GetAsyncKeyState.argtypes = [ctypes.c_int]
user32.GetAsyncKeyState.restype = ctypes.c_short
user32.GetClientRect.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.RECT)]
user32.ClientToScreen.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.POINT)]
user32.GetCursorPos.argtypes = [ctypes.POINTER(wintypes.POINT)]
user32.GetForegroundWindow.restype = wintypes.HWND
user32.GetWindowThreadProcessId.argtypes = [wintypes.HWND, ctypes.POINTER(wintypes.DWORD)]
user32.GetWindowThreadProcessId.restype = wintypes.DWORD
user32.GetKeyboardLayout.argtypes = [wintypes.DWORD]
user32.GetKeyboardLayout.restype = ctypes.c_void_p
KEYS = (0x10, 0x41, 0x53, 0x44, 0x46, 0x20)
SCANS = (0x2A, 0x1E, 0x1F, 0x20, 0x21, 0x39)


def held():
    return [key for key in KEYS if user32.GetAsyncKeyState(key) & 0x8000]


def wait_released():
    deadline = time.monotonic() + 2
    while held() and time.monotonic() < deadline:
        time.sleep(0.005)
    assert not held(), f"Keys still held: {held()}"


def available_port():
    with socket.socket() as reservation:
        reservation.bind(("127.0.0.1", 0))
        return reservation.getsockname()[1]


def main(focus_step, prepare_keyboard):
    assert not held(), "Release the six game keys before running this test"
    OUTPUT.mkdir(parents=True, exist_ok=True)
    metrics = {}
    lines = []
    target = subprocess.Popen([str(ROOT / "build/windows/tests/Release/ift_input_target.exe")],
                              stdout=subprocess.PIPE, text=True, creationflags=subprocess.CREATE_NO_WINDOW)
    host = None
    connection = None
    original_layout = None
    target_thread = None
    try:
        primary = int(target.stdout.readline().split()[2], 16)
        secondary = int(target.stdout.readline().split()[2], 16)
        def collect():
            for line in target.stdout:
                lines.append(line.strip())
        reader = threading.Thread(target=collect, daemon=True)
        reader.start()
        print(f"Primary 0x{primary:x}; secondary 0x{secondary:x}", flush=True)
        target_thread = user32.GetWindowThreadProcessId(primary, None)
        original_layout = user32.GetKeyboardLayout(target_thread)
        metrics["originalKeyboardLayout"] = hex(original_layout)
        if original_layout & 0xFFFF != 0x0409:
            assert prepare_keyboard, "The target must use the US English keyboard layout; use --prepare-keyboard for a guided switch"
            print("KEYBOARD STEP: select the installed US English layout in the input acceptance window", flush=True)
            deadline = time.monotonic() + 300
            while user32.GetKeyboardLayout(target_thread) & 0xFFFF != 0x0409:
                assert time.monotonic() < deadline, "Keyboard layout preparation timed out"
                time.sleep(0.05)
            time.sleep(0.3)
            assert not held(), "Keyboard-switch shortcut has not been released"
        metrics["testKeyboardLayout"] = hex(user32.GetKeyboardLayout(target_thread))
        deadline = time.monotonic() + 45
        while user32.GetForegroundWindow() != primary:
            if time.monotonic() >= deadline:
                raise AssertionError("Activate the InFalsusTouch input acceptance window to begin")
            time.sleep(0.1)
        port = available_port()
        with (OUTPUT / "host.log").open("w", encoding="utf-8") as log:
            host = subprocess.Popen([str(ROOT / "dist/InFalsusTouchHost.exe"), "--no-profile", "--no-video",
                                     "--window", hex(primary), "--port", str(port)], stdout=log, stderr=log,
                                    creationflags=subprocess.CREATE_NO_WINDOW)
            deadline = time.monotonic() + 5
            while connection is None:
                try:
                    connection = wire.connect(port)
                except ConnectionRefusedError:
                    assert host.poll() is None, "Host exited"
                    if time.monotonic() >= deadline:
                        raise
                    time.sleep(0.02)
            sequence = 3
            def send(kind, lane=0, value=0.0):
                nonlocal sequence
                reply = wire.exchange(connection, kind, sequence, lane=lane, value=value)
                sequence += 1
                return reply
            for lane in range(1, 7):
                assert send(2, lane)[4] == 0
            deadline = time.monotonic() + 0.5
            while time.monotonic() < deadline:
                send(7)
                assert held() == list(KEYS), held()
                time.sleep(0.05)
            client = wintypes.RECT()
            origin = wintypes.POINT()
            assert user32.GetClientRect(primary, ctypes.byref(client))
            assert user32.ClientToScreen(primary, ctypes.byref(origin))
            positions = []
            for x in (0.0, 0.5, 1.0):
                assert send(4, value=x)[4] in (0, 4)
                time.sleep(0.03)
                actual = wintypes.POINT()
                assert user32.GetCursorPos(ctypes.byref(actual))
                expected = (origin.x + round((0.05 + 0.9 * x) * (client.right - 1)),
                            origin.y + round(0.5 * (client.bottom - 1)))
                assert abs(actual.x - expected[0]) <= 1 and abs(actual.y - expected[1]) <= 1, (x, expected, actual.x, actual.y)
                positions.append({"normalizedX": x, "expected": expected, "actual": [actual.x, actual.y]})
            metrics["fieldPositions"] = positions
            send(6)
            wait_released()
            time.sleep(0.05)
            for key, scan in zip(KEYS, SCANS):
                assert f"KEY 1 DOWN {key} {scan}" in lines, lines
                assert f"KEY 1 UP {key} {scan}" in lines, lines
            print("PASS: six physical scan codes, concurrent hold, client Field endpoints and release", flush=True)
            if focus_step:
                for lane in range(1, 7):
                    send(2, lane)
                print("FOCUS STEP: activate the InFalsusTouch focus-loss test window now", flush=True)
                deadline = time.monotonic() + 60
                while user32.GetForegroundWindow() == primary:
                    assert time.monotonic() < deadline, "Focus-loss step timed out"
                    send(7)
                    time.sleep(0.03)
                lost_at = time.monotonic()
                assert user32.GetForegroundWindow() == secondary, "Unexpected foreground application"
                wait_released()
                metrics["focusReleaseObservedMs"] = (time.monotonic() - lost_at) * 1000
                assert send(2, 1)[4] == 1, "Inactive target accepted a new key"
                assert not held()
                print("PASS: foreground loss releases all keys and blocks new downs", flush=True)
            else:
                send(2, 2)
                connection.close()
                connection = None
                wait_released()
                connection = wire.connect(port)
                sequence = 3
                send(2, 4)
                started = time.monotonic()
                wire.expect_closed(connection)
                wait_released()
                metrics["silentLinkReleaseMs"] = (time.monotonic() - started) * 1000
                assert 350 <= metrics["silentLinkReleaseMs"] < 1500, metrics
                print("PASS: real key state clears on socket EOF and silent-link watchdog", flush=True)
            metrics["passed"] = True
            print(json.dumps(metrics, indent=2), flush=True)
    finally:
        if connection is not None:
            connection.close()
        if host is not None:
            wait_released()
            if host.poll() is None:
                host.terminate()
                host.wait(timeout=5)
        if prepare_keyboard and target_thread is not None and original_layout is not None and target.poll() is None:
            if user32.GetKeyboardLayout(target_thread) != original_layout:
                print("RESTORE STEP: restore the input test window's original keyboard layout", flush=True)
                deadline = time.monotonic() + 300
                while user32.GetKeyboardLayout(target_thread) != original_layout and time.monotonic() < deadline:
                    time.sleep(0.1)
                restored = user32.GetKeyboardLayout(target_thread) == original_layout
                metrics["keyboardLayoutRestored"] = restored
                print(f"Keyboard layout restored: {restored}", flush=True)
        if target.poll() is None:
            target.terminate()
            target.wait(timeout=5)
        suffix = "-focus" if focus_step else ""
        (OUTPUT / f"metrics{suffix}.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")
        (OUTPUT / f"target{suffix}.log").write_text("\n".join(lines) + "\n", encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--focus-step", action="store_true")
    parser.add_argument("--prepare-keyboard", action="store_true")
    args = parser.parse_args()
    main(args.focus_step, args.prepare_keyboard)
