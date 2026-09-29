"""Record Android scheduling and buffer/fence events around the USB video test."""

import argparse
from datetime import datetime, timezone
import importlib.util
import json
import pathlib
import re
import subprocess
import sys
import time


ROOT = pathlib.Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("device_tools", ROOT / "tests/video-device.py")
device = importlib.util.module_from_spec(spec)
spec.loader.exec_module(device)
CONFIG = """buffers { size_kb: 131072 fill_policy: RING_BUFFER }
buffers { size_kb: 8192 fill_policy: RING_BUFFER }
duration_ms: 60000
incremental_state_config { clear_period_ms: 5000 }
data_sources { config {
  name: "linux.ftrace"
  ftrace_config {
    ftrace_events: "sched/sched_switch"
    ftrace_events: "sched/sched_waking"
    ftrace_events: "power/cpu_frequency"
    ftrace_events: "power/cpu_idle"
    ftrace_events: "ftrace/print"
    atrace_categories: "gfx"
    atrace_categories: "view"
    atrace_categories: "video"
    atrace_apps: "dev.cedarflake.infalsustouch"
    buffer_size_kb: 4096
    drain_period_ms: 250
  }
} }
data_sources { config {
  name: "linux.process_stats"
  target_buffer: 1
  process_stats_config { scan_all_processes_on_start: true }
} }
data_sources { config { name: "android.surfaceflinger.frametimeline" target_buffer: 1 } }
data_sources { config { name: "android.surfaceflinger.frame" target_buffer: 1 } }
"""


def is_trace_process(pid, remote):
    command = subprocess.run([str(device.ADB), "-d", "shell", "cat", f"/proc/{pid}/cmdline"],
                             capture_output=True, creationflags=subprocess.CREATE_NO_WINDOW, timeout=10)
    arguments = command.stdout.split(b"\0")
    return (command.returncode == 0 and remote.encode() in arguments and
            arguments[0].rsplit(b"/", 1)[-1] == b"perfetto")


def finish_trace(pid, remote, output):
    if is_trace_process(pid, remote):
        device.adb("shell", "kill", "-TERM", pid)
        deadline = time.monotonic() + 15
        while is_trace_process(pid, remote):
            if time.monotonic() > deadline:
                raise TimeoutError("The test's Perfetto session did not stop")
            time.sleep(0.25)
    result = device.adb("pull", remote, str(output / "video.pftrace"))
    (output / "perfetto-pull.txt").write_text(result, encoding="utf-8")
    assert (output / "video.pftrace").stat().st_size > 0, "Empty Perfetto trace"
    device.adb("shell", "rm", remote)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", type=pathlib.Path, default=ROOT / "dist/InFalsusTouchHost.exe")
    parser.add_argument("--fps", type=int, choices=(60, 90, 120), default=60)
    parser.add_argument("--build-mode", choices=("debug", "profile"), default="profile")
    parser.add_argument("--skip-install", action="store_true")
    args = parser.parse_args()
    if not args.skip_install:
        app_apk, test_apk = device.apks(args.build_mode)
        print(device.adb("install", "-r", str(app_apk)), flush=True)
        print(device.adb("install", "-r", "-t", str(test_apk)), flush=True)
    run_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    output = ROOT / "build/video-system-trace" / f"{args.fps}fps-{args.build_mode}-{run_id}"
    output.mkdir(parents=True)
    remote = f"/data/misc/perfetto-traces/ift-{run_id}.pftrace"
    before = device.adb("exec-out", "run-as", device.APP, "cat", "shared_prefs/controller-settings.xml", binary=True)
    mappings = device.mappings()
    (output / "preferences-before.xml").write_bytes(before)
    (output / "config.pbtxt").write_text(CONFIG, encoding="utf-8")
    (output / "battery-before.txt").write_text(device.adb("shell", "dumpsys", "battery"), encoding="utf-8")
    (output / "perfetto-version.txt").write_text(device.adb("shell", "perfetto", "--version"), encoding="utf-8")
    summary = {"fps": args.fps, "buildMode": args.build_mode, "tracingOverhead": True}
    pid = None
    try:
        start = subprocess.run([str(device.ADB), "-d", "shell", "perfetto", "--background-wait",
                                "--txt", "-c", "-", "-o", remote], input=CONFIG.encode(), capture_output=True,
                               creationflags=subprocess.CREATE_NO_WINDOW, timeout=40)
        (output / "perfetto-start.txt").write_bytes(start.stdout + start.stderr)
        start.check_returncode()
        pid = start.stdout.decode("ascii").strip()
        if not re.fullmatch("[1-9][0-9]*", pid):
            raise RuntimeError(f"Unexpected Perfetto PID: {pid!r}")
        print(f"Recording Perfetto PID {pid}; evidence: {output}", flush=True)
        result = subprocess.run([sys.executable, str(ROOT / "tests/video-device.py"), "--host", str(args.host),
                                 "--build-mode", args.build_mode, "--skip-install", "--fps", str(args.fps),
                                 "--seconds", "10", "--output", str(output / "video")], capture_output=True,
                                creationflags=subprocess.CREATE_NO_WINDOW, timeout=150)
        (output / "video-run.txt").write_bytes(result.stdout + result.stderr)
        summary["videoExit"] = result.returncode
    finally:
        try:
            if pid is not None and re.fullmatch("[1-9][0-9]*", pid):
                finish_trace(pid, remote, output)
                summary["traceBytes"] = (output / "video.pftrace").stat().st_size
        finally:
            try:
                after = device.adb("exec-out", "run-as", device.APP, "cat", "shared_prefs/controller-settings.xml", binary=True)
                (output / "preferences-after.xml").write_bytes(after)
                summary["preferencesRestored"] = before == after
                summary["mappingsRestored"] = mappings == device.mappings()
                (output / "battery-after.txt").write_text(device.adb("shell", "dumpsys", "battery"), encoding="utf-8")
            finally:
                (output / "summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
                device.adb("shell", "am", "start", "-n", device.APP + "/dev.cedarflake.ift.MainActivity")
        assert summary["preferencesRestored"], "Phone preferences changed during the test"
        assert summary["mappingsRestored"], "ADB reverse mappings were not restored"
    print(json.dumps(summary, indent=2), flush=True)
    print(f"Saved trace and video test outcome: {output}", flush=True)
    return summary["videoExit"]


if __name__ == "__main__":
    sys.exit(main())
