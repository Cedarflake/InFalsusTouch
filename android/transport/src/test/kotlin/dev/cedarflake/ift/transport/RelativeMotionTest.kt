package dev.cedarflake.ift.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RelativeMotionTest {
  @Test fun pixelMotionUsesWireUnitsWithoutScalingByTouchArea() {
    val values = mutableListOf<Float>()
    forEachRelativeStep(100f, values::add)
    forEachRelativeStep(-50f, values::add)
    forEachRelativeStep(0f, values::add)
    assertEquals(listOf(100f / 1280, -50f / 1280), values)
  }

  @Test fun fastSwipesSplitIntoValidOrderedPacketsWithoutLosingDistance() {
    val values = mutableListOf<Float>()
    forEachRelativeStep(2400f, values::add)
    forEachRelativeStep(-2400f, values::add)
    assertEquals(listOf(1f, 0.875f, -1f, -0.875f), values)
    val codec = PacketCodec()
    values.forEach { codec.encode(Packet(MessageType.FIELD_RELATIVE, value = it)) }
    assertEquals(0f, values.sum())
  }

  @Test fun invalidMotionCannotProduceAnUnboundedPacketLoop() {
    for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, -32769f, 32769f)) {
      assertFailsWith<IllegalArgumentException> { forEachRelativeStep(value) { error("Must reject before emitting") } }
    }
  }
}
