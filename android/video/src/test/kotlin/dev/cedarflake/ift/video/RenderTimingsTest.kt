package dev.cedarflake.ift.video

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class RenderTimingsTest {
  @Test fun overflowRetiresOnlyTheOldestFrame() {
    val frames = RenderTimings(2)
    val oldest = timing()
    val pending = timing()
    val newest = timing()
    frames.add(10, oldest)
    frames.add(20, pending)
    frames.add(30, newest)
    assertNull(frames.take(10))
    assertSame(pending, frames.take(20))
    assertSame(newest, frames.take(30))
  }

  @Test fun handlesOutOfOrderCallbacksAndFlushWithoutLosingOtherFrames() {
    val frames = RenderTimings(3)
    val first = timing()
    val second = timing()
    val third = timing()
    frames.add(10, first)
    frames.add(20, second)
    frames.add(30, third)
    assertSame(second, frames.take(20))
    frames.add(40, timing())
    frames.add(10, first)
    assertSame(first, frames.take(10))
    assertSame(third, frames.take(30))
    frames.clear()
    assertNull(frames.take(40))
  }

  private fun timing() = FrameTiming(1, 2, 3, 4, 5, 6, 7)
}
