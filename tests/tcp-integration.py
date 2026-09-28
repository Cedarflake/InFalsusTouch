"""Exercise the actual Windows server without injecting system input."""

import argparse
import pathlib
import socket
import struct
import subprocess
import tempfile
import time


WIRE = struct.Struct(">4sBBBBIfQQ")


def packet(kind, sequence, lane=0, value=0.0):
    return WIRE.pack(b"IFT1", 1, kind, lane, 0, sequence, value, time.monotonic_ns(), 0)


def read_ack(connection, request):
    data = bytearray()
    while len(data) < WIRE.size:
        chunk = connection.recv(WIRE.size - len(data))
        assert chunk, "Connection closed before ACK"
        data.extend(chunk)
    ack = WIRE.unpack(data)
    sent = WIRE.unpack(request)
    assert ack[:4] == (b"IFT1", 1, 128, 0), ack
    assert ack[5] == sent[5] and ack[7] == sent[7], "ACK did not echo sequence/timestamp"
    return ack


def exchange(connection, kind, sequence, lane=0, value=0.0):
    request = packet(kind, sequence, lane, value)
    connection.sendall(request)
    return read_ack(connection, request)


def connect(port):
    connection = socket.create_connection(("127.0.0.1", port), timeout=2)
    connection.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
    exchange(connection, 1, 1)
    assert exchange(connection, 6, 2)[4] == 0
    return connection


def wait_for_trace(path, predicate):
    deadline = time.monotonic() + 2
    while time.monotonic() < deadline:
        lines = path.read_text().splitlines() if path.exists() else []
        if predicate(lines):
            return lines
        time.sleep(0.01)
    raise AssertionError(f"Trace condition not met: {lines}")


def expect_closed(connection):
    try:
        assert connection.recv(1) == b"", "Server did not reject invalid session"
    except ConnectionResetError:
        pass


def run(host):
    with socket.socket() as reservation:
        reservation.bind(("127.0.0.1", 0))
        port = reservation.getsockname()[1]
    with tempfile.TemporaryDirectory(prefix="ift-tcp-") as temporary:
        root = pathlib.Path(temporary)
        trace = root / "input.txt"
        with (root / "host.log").open("w") as log:
            process = subprocess.Popen(
                [str(host), "--dry-run", "--port", str(port), "--trace", str(trace)],
                stdout=log,
                stderr=log,
                creationflags=subprocess.CREATE_NO_WINDOW,
            )
            try:
                deadline = time.monotonic() + 5
                while True:
                    try:
                        connection = socket.create_connection(("127.0.0.1", port), timeout=0.2)
                        connection.settimeout(2)
                        break
                    except OSError:
                        assert process.poll() is None, "Host exited before listening"
                        if time.monotonic() > deadline:
                            raise
                        time.sleep(0.02)

                with connection:
                    hello = packet(1, 1)
                    for offset in range(0, 32, 3):
                        connection.sendall(hello[offset:offset + 3])
                    read_ack(connection, hello)
                    exchange(connection, 6, 2)
                    chord = [packet(2, lane + 2, lane) for lane in range(1, 7)]
                    connection.sendall(b"".join(chord))
                    for request in chord:
                        read_ack(connection, request)
                    exchange(connection, 4, 9, value=0.5)
                    exchange(connection, 3, 10, lane=3)
                lines = wait_for_trace(trace, lambda rows: sum(row.startswith("UP ") for row in rows) == 6)
                assert lines[:6] == [f"DOWN {lane}" for lane in range(1, 7)]
                assert "ABS 640 360" in lines, lines
                print("PASS: fragmented/coalesced packets, six-key hold, Field, EOF release")

                for test_name, invalid in (
                    ("reserved byte", lambda: packet(7, 4)[:-1] + b"\x01"),
                    ("wrong sequence", lambda: packet(7, 9)),
                    ("NaN Field", lambda: packet(4, 4, value=float("nan"))),
                    ("invalid lane", lambda: packet(2, 4, lane=7)),
                ):
                    before = trace.read_text().splitlines().count("UP 2")
                    with connect(port) as connection:
                        exchange(connection, 2, 3, lane=2)
                        connection.sendall(invalid())
                        expect_closed(connection)
                    wait_for_trace(trace, lambda rows: rows.count("UP 2") == before + 1)
                    print(f"PASS: {test_name} rejects connection and releases hold")

                with connect(port) as connection:
                    exchange(connection, 2, 3, lane=4)
                    start = time.monotonic()
                    expect_closed(connection)
                    assert 0.35 <= time.monotonic() - start < 1.5, "Watchdog deadline exceeded"
                wait_for_trace(trace, lambda rows: rows.count("UP 4") == 2)
                print("PASS: silent connection watchdog releases hold")

                with connect(port) as connection:
                    exchange(connection, 2, 3, lane=5)
                    connection.sendall(packet(7, 4)[:7])
                    expect_closed(connection)
                wait_for_trace(trace, lambda rows: rows.count("UP 5") == 2)
                print("PASS: truncated packet timeout releases hold")

                with connect(port) as connection:
                    exchange(connection, 2, 3, lane=6)
                    exchange(connection, 3, 4, lane=6)
                    exchange(connection, 6, 5)
                lines = wait_for_trace(trace, lambda rows: rows.count("UP 6") == 2)
                held = set()
                for line in lines:
                    if line.startswith("DOWN "):
                        held.add(line.split()[1])
                    elif line.startswith("UP "):
                        held.discard(line.split()[1])
                assert not held, held
                print("PASS: reconnect starts clean; all injected state released")
            finally:
                process.terminate()
                process.wait(timeout=5)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", type=pathlib.Path, required=True)
    arguments = parser.parse_args()
    run(arguments.host.resolve())
