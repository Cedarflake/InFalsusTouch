"""Verify one hardware bitstream reaches several viewers while a slow viewer is isolated."""

import argparse
import json
import pathlib
import socket
import struct
import subprocess
import threading
import time


HEADER = struct.Struct(">4sBBHIIQQQQHHHHII")


def read_exact(stream, count):
    data = bytearray()
    while len(data) < count:
        chunk = stream.recv(count - len(data))
        if not chunk:
            raise EOFError("Video closed")
        data.extend(chunk)
    return data


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--window")
    args = parser.parse_args()
    output = pathlib.Path("build/multiplayer-video-test")
    output.mkdir(parents=True, exist_ok=True)
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        control_port = probe.getsockname()[1]
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        video_port = probe.getsockname()[1]
    pattern = None if args.window else subprocess.Popen([str(pathlib.Path("build/windows/tests/Release/ift_video_pattern.exe").resolve())],
        stdout=subprocess.PIPE, text=True, creationflags=subprocess.CREATE_NO_WINDOW)
    host = None
    sockets = []
    failures = []
    results = [{} for _ in range(6)]
    try:
        window = args.window or pattern.stdout.readline().strip()
        assert window.startswith("0x")
        with (output / "host.log").open("w", encoding="utf-8") as log:
            host = subprocess.Popen([str(pathlib.Path("build/windows/windows/Release/InFalsusTouchHost.exe").resolve()), "--dry-run", "--video", "--no-profile", "--fps", "60",
                "--window", window, "--port", str(control_port), "--video-port", str(video_port)],
                stdout=log, stderr=log, creationflags=subprocess.CREATE_NO_WINDOW)
            deadline = time.monotonic() + 15
            while True:
                try:
                    slow = socket.socket()
                    slow.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 1024)
                    slow.settimeout(2)
                    slow.connect(("127.0.0.1", video_port))
                    slow.sendall(struct.pack(">4sBBH", b"IFV1", 2, 0, 60))
                    sockets.append(slow)
                    break
                except OSError:
                    slow.close()
                    assert time.monotonic() < deadline and host.poll() is None
                    time.sleep(0.05)

            def viewer(index):
                try:
                    with socket.create_connection(("127.0.0.1", video_port), timeout=5) as stream:
                        stream.sendall(struct.pack(">4sBBH", b"IFV1", 2, 0, 60))
                        sequence = 0
                        first = None
                        captures = {}
                        while first is None or time.monotonic() - first < 6:
                            header = HEADER.unpack(read_exact(stream, HEADER.size))
                            magic, version, kind, flags, size, seq, capture, encoded, sent, pts, width, height, fps, reserved, bitrate, tail = header
                            assert magic == b"IFV1" and version == 2 and 0 < size <= 4 * 1024 * 1024
                            read_exact(stream, size)
                            if kind == 1:
                                assert seq == 0 and first is None
                                continue
                            assert kind == 2 and seq == sequence + 1 and capture <= encoded <= sent
                            if first is None:
                                assert flags == 1
                                first = time.monotonic()
                            sequence = seq
                            captures[capture] = encoded
                        results[index] = {"frames": sequence, "fps": sequence / (time.monotonic() - first), "captures": captures}
                except Exception as error:
                    failures.append(f"viewer {index}: {error}")

            threads = []
            for index in range(6):
                task = threading.Thread(target=viewer, args=(index,), daemon=True)
                task.start()
                threads.append(task)
                time.sleep(0.12)
            for task in threads:
                task.join(timeout=12)
                assert not task.is_alive(), "Viewer did not finish"
            assert not failures, failures
            assert all(result["fps"] >= 55 for result in results), results
            common = set(results[0]["captures"])
            for result in results[1:]:
                common.intersection_update(result["captures"])
            assert len(common) > 180, "Viewers did not share enough frames"
            for stamp in common:
                assert len({result["captures"][stamp] for result in results}) == 1, "Viewers were encoded independently"
            log.flush()
            host_log = (output / "host.log").read_text(encoding="utf-8")
            assert "bounded queue" in host_log or "100 ms" in host_log, "Slow viewer was not isolated"
            metrics = {"viewers": [{key: value for key, value in result.items() if key != "captures"} for result in results],
                "sharedEncodedFrames": len(common), "slowViewerIsolated": True, "physicalPhones": 0}
            (output / "metrics.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")
            print(json.dumps(metrics, indent=2))
    finally:
        for stream in sockets:
            stream.close()
        for process in (host, pattern):
            if process is not None and process.poll() is None:
                process.terminate()
                process.wait(timeout=5)


if __name__ == "__main__":
    main()
