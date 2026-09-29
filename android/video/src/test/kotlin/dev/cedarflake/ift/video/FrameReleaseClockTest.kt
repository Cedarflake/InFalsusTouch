package dev.cedarflake.ift.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameReleaseClockTest {
  @Test fun steadySixtyFpsDoesNotAddAFrameOfLatency() {
    val clock = FrameReleaseClock(60)
    repeat(60) { frame ->
      val now = 1_000_000_000L + frame * 16_666_666
      assertEquals(now, clock.next(now))
    }
  }

  @Test fun absorbsJitterWithoutSchedulingTwoFramesForTheSameDeadline() {
    val clock = FrameReleaseClock(120)
    val origin = 1_000_000_000L
    val arrivals = listOf(0L, 5_000_000, 17_000_000, 23_000_000, 31_000_000)
    val deadlines = arrivals.map { clock.next(origin + it) }
    for ((first, last) in deadlines.zipWithNext()) assertTrue(last - first >= 8_333_333)
    for ((arrival, deadline) in arrivals.zip(deadlines)) assertTrue(deadline - (origin + arrival) <= 24_999_999)
  }

  @Test fun stalledPlaybackAndFlushCannotAccumulateOldDeadlines() {
    val clock = FrameReleaseClock(120)
    val origin = 1_000_000_000L
    repeat(20) { assertTrue(clock.next(origin) <= origin + 24_999_999) }
    assertEquals(origin + 516_666_666, clock.next(origin + 500_000_000))
    clock.reset()
    assertEquals(origin + 16_666_666, clock.next(origin))
  }
}
