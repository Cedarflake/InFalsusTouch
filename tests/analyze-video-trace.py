"""Match Android video buffers by layer and frame number, without cross-device clocks."""

import argparse
import csv
import io
import json
import math
import pathlib
import statistics
import subprocess


ROOT = pathlib.Path(__file__).resolve().parent.parent
APP = "dev.cedarflake.infalsustouch"
STAGES = ("Queue", "Latch", "PresentFenceSignaled")


def distribution(values):
    if not values:
        return None
    ordered = sorted(values)
    return {"samples": len(ordered), "meanMs": statistics.mean(ordered),
            "p95Ms": ordered[math.ceil(len(ordered) * 0.95) - 1], "maxMs": ordered[-1]}


def cadence(timestamps):
    ordered = sorted(set(timestamps))
    intervals = [(last - first) / 1e6 for first, last in zip(ordered, ordered[1:])]
    summary = distribution(intervals)
    if summary is not None:
        summary["p5Ms"] = sorted(intervals)[math.ceil(len(intervals) * 0.05) - 1]
        summary["medianMs"] = statistics.median(intervals)
    span = (ordered[-1] - ordered[0]) / 1e9 if len(ordered) > 1 else 0.0
    return {"events": len(timestamps), "uniqueTimestamps": len(ordered), "spanSeconds": span,
            "uniqueEventRateHz": (len(ordered) - 1) / span if span else None, "intervals": summary}


def match_frames(events, start, end):
    frames = {}
    for event in events:
        stages = frames.setdefault((event["layer_name"], int(event["frame_number"])), {})
        stages.setdefault(event["name"], set()).add(int(event["ts"]))
    matched = []
    counts = {"queued": 0, "latched": 0, "presented": 0, "complete": 0, "ambiguous": 0, "invalidOrder": 0}
    for (layer, number), stages in sorted(frames.items()):
        queued = stages.get("Queue", set())
        if not queued or not any(start <= value < end for value in queued):
            continue
        counts["queued"] += 1
        counts["latched"] += bool(stages.get("Latch"))
        counts["presented"] += bool(stages.get("PresentFenceSignaled"))
        if any(len(values) > 1 for values in stages.values()):
            counts["ambiguous"] += 1
            continue
        if not all(stage in stages for stage in STAGES):
            continue
        queue, latch, present = (next(iter(stages[stage])) for stage in STAGES)
        if not queue <= latch <= present:
            counts["invalidOrder"] += 1
            continue
        counts["complete"] += 1
        matched.append({"layer": layer, "frame": number, "queueNs": queue, "latchNs": latch,
                        "presentNs": present, "queueToLatchMs": (latch - queue) / 1e6,
                        "latchToPresentMs": (present - latch) / 1e6, "queueToPresentMs": (present - queue) / 1e6})
    return counts, matched


def analyze(directory, processor):
    trace = directory / "video.pftrace"
    (directory / "analysis.json").unlink(missing_ok=True)

    def query(name, sql):
        (directory / f"{name}.sql").write_text(sql, encoding="utf-8")
        result = subprocess.run([str(processor.resolve()), "query", str(trace.resolve()), sql], capture_output=True,
                                creationflags=subprocess.CREATE_NO_WINDOW, timeout=90)
        (directory / f"{name}.csv").write_bytes(result.stdout)
        (directory / f"{name}-import.txt").write_bytes(result.stderr)
        result.check_returncode()
        return list(csv.DictReader(io.StringIO(result.stdout.decode("utf-8"))))

    health = query("trace-health", "SELECT name,idx,severity,value FROM stats WHERE value != 0 AND "
                   "(severity != 'info' OR name LIKE '%overwritten%' OR name LIKE '%lost%')")
    sessions = query("codec-sessions", f"""SELECT t.utid,t.upid,t.tid,MIN(s.ts) firstNs,MAX(s.ts+s.dur) lastNs,COUNT(*) outputs
FROM slice s JOIN thread_track tr ON tr.id=s.track_id JOIN thread t USING(utid) JOIN process p USING(upid)
WHERE p.name='{APP}' AND s.name='onReleaseOutputBuffer'
GROUP BY t.utid ORDER BY outputs DESC""")
    if not sessions:
        raise ValueError("No codec output session in this trace; enable video atrace events")
    session = sessions[0]
    start, end = int(session["firstNs"]) + 3_000_000_000, int(session["lastNs"]) - 2_000_000_000
    if end <= start:
        raise ValueError("Codec session is too short after excluding startup and shutdown")
    events = query("video-buffer-events", f"""SELECT layer_name,frame_number,name,ts FROM frame_slice
WHERE layer_name LIKE 'SurfaceView[{APP}/%' AND name IN ('Queue','Latch','PresentFenceSignaled')
ORDER BY ts""")
    layer_counts = {}
    for event in events:
        if event["name"] == "Queue" and start <= int(event["ts"]) < end:
            layer_counts[event["layer_name"]] = layer_counts.get(event["layer_name"], 0) + 1
    if not layer_counts:
        raise ValueError("No video buffer events in the codec window; enable android.surfaceflinger.frame")
    layer = max(layer_counts, key=layer_counts.get)
    counts, matched = match_frames([event for event in events if event["layer_name"] == layer], start, end)
    queued = [int(event["ts"]) for event in events if event["layer_name"] == layer and
              event["name"] == "Queue" and start <= int(event["ts"]) < end]
    with (directory / "matched-video-frames.csv").open("w", newline="", encoding="utf-8") as output:
        writer = csv.DictWriter(output, fieldnames=("layer", "frame", "queueNs", "latchNs", "presentNs",
                                                   "queueToLatchMs", "latchToPresentMs", "queueToPresentMs"))
        writer.writeheader()
        writer.writerows(matched)
    upid = int(session["upid"])
    pulse_events = query("choreographer-events", f"""SELECT t.tid,t.name thread,s.ts,s.dur,s.name
FROM slice s JOIN thread_track tr ON tr.id=s.track_id JOIN thread t USING(utid)
WHERE t.upid={upid} AND s.name LIKE 'Choreographer#doFrame%' AND s.ts>={start} AND s.ts<{end}
ORDER BY s.ts""")
    pulse_threads = {}
    for event in pulse_events:
        pulse_threads.setdefault((int(event["tid"]), event["thread"]), []).append(int(event["ts"]))
    pulses = [{"tid": tid, "thread": name, **cadence(timestamps)}
              for (tid, name), timestamps in sorted(pulse_threads.items())]
    scheduling = query("video-runnable-waits", f"""WITH spans AS (
SELECT t.tid,t.name,st.state,MIN(st.ts+st.dur,{end})-MAX(st.ts,{start}) dur
FROM thread_state st JOIN thread t USING(utid)
WHERE t.upid={upid} AND (t.name LIKE 'ift-video%' OR t.name IN ('MediaCodec_loop','CodecLooper'))
AND st.state IN ('R','R+') AND st.dur>0 AND st.ts<{end} AND st.ts+st.dur>{start})
SELECT tid,name,state,COUNT(*) samples,AVG(dur)/1e6 meanMs,PERCENTILE(dur,95)/1e6 p95Ms,MAX(dur)/1e6 maxMs
FROM spans GROUP BY tid,state""")
    budgets = query("display-work-duration", f"""WITH values_in_time AS (
SELECT t.id,t.name,c.ts,c.value,LEAD(c.ts,1,{end}) OVER (PARTITION BY t.id ORDER BY c.ts) next_ts
FROM counter c JOIN track t ON t.id=c.track_id
WHERE t.name LIKE 'VsyncWorkDuration%' AND c.ts<{end})
SELECT name,value ns,SUM(MIN(next_ts,{end})-MAX(ts,{start}))/1e9 seconds
FROM values_in_time WHERE next_ts>{start} GROUP BY id,value""")
    result = {"windowStartNs": start, "windowEndNs": end, "windowSeconds": (end - start) / 1e9,
              "windowSelection": "Most codec outputs; exclude first 3 seconds and final 2 seconds",
              "codecSession": session, "layer": layer, "frameCounts": counts, "traceHealth": health,
              "queueToLatch": distribution([frame["queueToLatchMs"] for frame in matched]),
              "latchToPresent": distribution([frame["latchToPresentMs"] for frame in matched]),
              "queueToPresent": distribution([frame["queueToPresentMs"] for frame in matched]),
              "queueCadence": cadence(queued),
              "completePresentCadence": cadence([frame["presentNs"] for frame in matched]),
              "choreographerCallbacks": pulses,
              "schedulingWaits": scheduling, "displayWorkDurations": budgets,
              "limitations": ["Tracing adds overhead; this is not an untraced throughput benchmark",
                              "PresentFenceSignaled is an OS timestamp, not a physical screen measurement",
                              "All three latency distributions use the same complete buffer frames",
                              "Missing Latch/Present events stay missing; they are not zero latency",
                              "Cadence uses distinct timestamps; missing events can lengthen observed gaps",
                              "Choreographer callback entry cadence is not physical screen refresh"]}
    (directory / "analysis.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    if not matched:
        raise ValueError("No complete video frame chains were matched")
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=pathlib.Path)
    parser.add_argument("--processor", type=pathlib.Path, default=ROOT / ".tools/perfetto/trace_processor_shell.exe")
    args = parser.parse_args()
    print(json.dumps(analyze(args.directory, args.processor), indent=2))


if __name__ == "__main__":
    main()
