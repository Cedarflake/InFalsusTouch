package dev.cedarflake.ift.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VideoStatisticsTest {
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
