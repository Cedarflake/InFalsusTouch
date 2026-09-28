package dev.cedarflake.ift.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class VideoLatencyWindowTest {
  private fun timing(queue: Long, decode: Long, present: Long): FrameTiming {
    val receive = 9_000_000_000L
    val submit = receive + queue * 1_000_000
    val decoded = submit + decode * 1_000_000
    return FrameTiming(100, 200, 300, receive, submit, decoded, decoded + present * 1_000_000)
  }

  @Test fun separatesLocalStagesWithoutSubtractingPcAndPhoneClocks() {
    val window = VideoLatencyWindow()
    assertNull(window.snapshot())
    window.record(timing(2, 5, 11))
    val result = assertNotNull(window.snapshot())
    assertEquals(1, result.samples)
    assertEquals(LatencyDistribution(2.0, 2.0), result.receiveToSubmit)
    assertEquals(LatencyDistribution(5.0, 5.0), result.decode)
    assertEquals(LatencyDistribution(11.0, 11.0), result.decodeToPresent)
    assertEquals(LatencyDistribution(18.0, 18.0), result.receiveToPresent)
  }

  @Test fun retainsTheMostRecentFramesAndComputesNearestRankP95() {
    val window = VideoLatencyWindow(20)
    window.record(timing(0, 0, 1000))
    for (milliseconds in 1L..20L) window.record(timing(0, 0, milliseconds))
    val result = assertNotNull(window.snapshot())
    assertEquals(20, result.samples)
    assertEquals(LatencyDistribution(10.5, 19.0), result.receiveToPresent)
    assertEquals(result, window.snapshot())
  }
}
