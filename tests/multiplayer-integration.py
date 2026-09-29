"""Exercise seven real TCP controllers, independent holds, display masks and live IF key sync."""

import argparse
import json
import pathlib
import socket
import struct
import subprocess
import threading
import time


WIRE = struct.Struct(">4sBBBBIfQQ")
DEFAULT_KEYS = [51, 15, 33, 18, 20, 1]


def write_preferences(path, keys):
    path.write_text("".join(
        f'k "keybind_BottomLane{index}"\nv {key}\nk "keybind_state_BottomLane{index}"\nv 0\n'
        for index, key in enumerate(keys)), encoding="utf-8")


def await_condition(predicate, timeout=3):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(0.01)
    raise AssertionError("Condition did not become true")


class Peer:
    def __init__(self, port, fps=0):
        self.socket = socket.create_connection(("127.0.0.1", port), timeout=2)
        self.socket.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        self.sequence = 0
        self.lock = threading.Lock()
        self.stop = threading.Event()
        self.configuration = None
        self.failure = None
        self.request(1, value=fps)
        self.arm()
        self.thread = threading.Thread(target=self.heartbeat, daemon=True)
        self.thread.start()

    def request(self, kind, lane=0, value=0.0):
        with self.lock:
            self.sequence += 1
            stamp = time.monotonic_ns()
            self.socket.sendall(WIRE.pack(b"IFT1", 3, kind, lane, 0, self.sequence, value, stamp, 0))
            while True:
                data = bytearray()
                while len(data) < WIRE.size:
                    chunk = self.socket.recv(WIRE.size - len(data))
                    if not chunk:
                        raise EOFError("Controller closed")
                    data.extend(chunk)
                packet = WIRE.unpack(data)
                assert packet[:2] == (b"IFT1", 3), packet
                if packet[2] == 129:
                    self.configuration = {
                        "controls": packet[3], "bindingStatus": packet[4], "peers": int(packet[6]),
                        "keys": list(packet[7].to_bytes(6, "big")),
                    }
                    continue
                assert packet[2] == 128 and packet[5] == self.sequence and packet[7] == stamp, packet
                return packet[4]

    def arm(self):
        assert self.request(6) != 1

    def heartbeat(self):
        try:
            while not self.stop.wait(0.06):
                self.request(7)
        except (OSError, EOFError) as error:
            if not self.stop.is_set():
                self.failure = str(error)

    def suspend(self):
        self.stop.set()
        self.thread.join(timeout=3)

    def close(self):
        self.suspend()
        self.socket.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", type=pathlib.Path, default=pathlib.Path("dist/InFalsusTouchHost.exe"))
    args = parser.parse_args()
    output = pathlib.Path("build/multiplayer-test")
    output.mkdir(parents=True, exist_ok=True)
    prefs = output / "test-user.prefs"
    trace = output / "input-trace.txt"
    write_preferences(prefs, DEFAULT_KEYS)
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        port = probe.getsockname()[1]
    peers = []
    checks = []
    with (output / "host.log").open("w", encoding="utf-8") as log:
        host = subprocess.Popen([str(args.host.resolve()), "--dry-run", "--port", str(port),
            "--trace", str(trace.resolve()), "--bindings", str(prefs.resolve())], stdout=log, stderr=log,
            creationflags=subprocess.CREATE_NO_WINDOW)
        try:
            def connect_first():
                try:
                    peers.append(Peer(port))
                    return True
                except ConnectionRefusedError:
                    assert host.poll() is None
                    return False
            await_condition(connect_first, 8)
            peers.extend(Peer(port) for _ in range(6))
            await_condition(lambda: all(peer.configuration and peer.configuration["peers"] == 7 for peer in peers))
            with socket.create_connection(("127.0.0.1", port), timeout=2) as extra:
                try:
                    assert extra.recv(1) == b"", "Eighth controller was not rejected"
                except ConnectionResetError:
                    pass
            checks.append("seven simultaneous clients; eighth rejected")
            rows = lambda: trace.read_text().splitlines()
            peers[0].request(2, lane=2)
            peers[1].request(2, lane=2)
            peers[2].request(2, lane=5)
            assert rows().count("DOWN 2") == 1
            peers[0].close()
            await_condition(lambda: peers[1].configuration["peers"] == 6)
            assert "UP 2" not in rows() and "UP 5" not in rows()
            peers[1].request(3, lane=2)
            assert rows().count("UP 2") == 1 and "UP 5" not in rows()
            peers[2].request(3, lane=5)
            checks.append("overlapping key refcounts and per-device disconnect cleanup")
            peers[3].request(10, value=0)
            peers[3].arm()
            before = rows()
            peers[3].request(2, lane=3)
            peers[3].request(4, value=0.5)
            assert rows() == before
            peers[3].request(10, value=8)
            peers[3].arm()
            peers[3].request(2, lane=4)
            assert peers[1].configuration["controls"] == 127
            checks.append("hidden controls rejected; other phones keep independent selections")
            peers[1].request(8)
            peers[1].request(4, value=0.2)
            peers[2].request(8)
            before = rows()
            assert peers[2].request(4, value=0.8) == 2
            assert rows() == before
            peers[1].request(9)
            assert peers[2].request(4, value=0.8) == 4
            assert len(rows()) == len(before) + 1
            peers[2].request(9)
            checks.append("Field ownership stays stable and hands over after release")
            peers[6].request(2, lane=6)
            peers[6].suspend()
            await_condition(lambda: "UP 6" in rows())
            assert "UP 4" not in rows()
            checks.append("one watchdog timeout does not release other devices")
            changed = [51, 15, 33, 24, 20, 1]
            write_preferences(prefs, changed)
            await_condition(lambda: all(peer.configuration["keys"] == changed for peer in peers[1:6]))
            assert "UP 4" in rows()
            assert peers[3].request(2, lane=4) == 1
            peers[3].arm()
            peers[3].request(2, lane=4)
            peers[3].request(3, lane=4)
            checks.append("live binding changes release old holds and require a fresh barrier")
            prefs.write_text('k "keybind_BottomLane0"\nv 999\n', encoding="utf-8")
            await_condition(lambda: peers[3].configuration["bindingStatus"] == 2)
            assert peers[3].request(6) == 1
            write_preferences(prefs, changed)
            await_condition(lambda: peers[3].configuration["bindingStatus"] == 0)
            peers[3].arm()
            checks.append("invalid binding files pause input and recover without touching game saves")
            assert not [peer.failure for peer in peers[1:6] if peer.failure]
            result = {"checks": checks, "physicalPhones": 0, "simulatedTcpControllers": 7}
            (output / "metrics.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
            print(json.dumps(result, indent=2))
        finally:
            for peer in peers:
                peer.close()
            host.terminate()
            host.wait(timeout=5)


if __name__ == "__main__":
    main()
