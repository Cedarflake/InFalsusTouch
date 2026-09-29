package dev.cedarflake.ift.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VideoStatisticsTest {
  @Test fun cadenceRetainsGapsAcrossReportBoundariesAndIgnoresOldCallbacks() {
    val cadence = FrameCadence()
    cadence.record(1_000_000_000)
    cadence.record(1_008_333_333)
    assertEquals(8.333333, cadence.takeMaxGapMs())
    cadence.record(1_508_333_333)
    cadence.record(1_005_000_000)
    assertEquals(500.0, cadence.takeMaxGapMs())
    cadence.record(1_516_666_666)
    assertEquals(8.333333, cadence.takeMaxGapMs())
  }

  @Test fun expiredTimingDoesNotDiscardAPresentationCallback() {
    var now = 1_000_000_000L
    val statistics = VideoStatistics { now }
    statistics.presented(null)
    statistics.presented(FrameTiming(1, 2, 3, 1_010_000_000, 1_012_000_000, 1_014_000_000, 1_016_000_000))
    now += 1_000_000_000L
    val sample = assertNotNull(statistics.snapshot(0, 0))
    assertEquals(2L, sample.presentedFrames)
    assertEquals(2.0, sample.presentFps)
    assertEquals(1L, sample.untrackedPresentedFrames)
    assertEquals(1, assertNotNull(sample.latency).samples)
    assertEquals(6.0, sample.receiveToPresentMs)
  }
}
