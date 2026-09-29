"""Check frame correlation without turning missing or reordered events into latency."""

import importlib.util
import pathlib
import unittest


spec = importlib.util.spec_from_file_location("trace_analysis", pathlib.Path(__file__).with_name("analyze-video-trace.py"))
analysis = importlib.util.module_from_spec(spec)
spec.loader.exec_module(analysis)


def event(layer, frame, stage, ms):
    return {"layer_name": layer, "frame_number": frame, "name": stage, "ts": ms * 1_000_000}


class FrameCorrelationTest(unittest.TestCase):
    def test_layers_and_missing_fences_do_not_fabricate_complete_frames(self):
        events = [event("video", 1, "Queue", 10), event("video", 1, "Latch", 13),
                  event("video", 1, "PresentFenceSignaled", 20), event("video", 2, "Queue", 30),
                  event("video", 2, "Latch", 34), event("another", 2, "PresentFenceSignaled", 40),
                  event("video", 3, "Queue", 50)]
        counts, frames = analysis.match_frames(events, 10_000_000, 50_000_000)
        self.assertEqual({"queued": 2, "latched": 2, "presented": 1, "complete": 1,
                          "ambiguous": 0, "invalidOrder": 0}, counts)
        self.assertEqual((3, 7, 10), tuple(frames[0][key] for key in
                                         ("queueToLatchMs", "latchToPresentMs", "queueToPresentMs")))

    def test_duplicate_observations_and_invalid_order_are_distinguished(self):
        events = [event("video", 1, "Queue", 10), event("video", 1, "Queue", 10),
                  event("video", 1, "Latch", 12), event("video", 1, "PresentFenceSignaled", 15),
                  event("video", 2, "Queue", 20), event("video", 2, "Queue", 21),
                  event("video", 3, "Queue", 30), event("video", 3, "Latch", 28),
                  event("video", 3, "PresentFenceSignaled", 35)]
        counts, frames = analysis.match_frames(events, 0, 40_000_000)
        self.assertEqual(1, counts["complete"])
        self.assertEqual(1, counts["ambiguous"])
        self.assertEqual(1, counts["invalidOrder"])
        self.assertEqual(1, len(frames))
        self.assertIsNone(analysis.distribution([]))


if __name__ == "__main__":
    unittest.main()
