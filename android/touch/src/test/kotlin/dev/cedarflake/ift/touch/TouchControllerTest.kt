package dev.cedarflake.ift.touch

import dev.cedarflake.ift.settings.ControlSettings
import dev.cedarflake.ift.settings.FieldMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TouchControllerTest {
  private class Sink : TouchSink {
    val events = mutableListOf<String>()
    var fieldBegins = 0
    var fieldEnds = 0
    override fun fieldBegin() { fieldBegins++ }
    override fun fieldEnd() { fieldEnds++ }
    override fun laneDown(lane: Int) { events += "D$lane" }
    override fun laneUp(lane: Int) { events += "U$lane" }
    override fun fieldAbsolute(x: Float) { events += "A$x" }
    override fun fieldRelative(deltaX: Float) { events += "R$deltaX" }
    override fun releaseAll() { events += "CLEAR" }
  }

  private val sink = Sink()
  private val geometry = TouchGeometry(600f, 400f, ControlSettings())
  private val touch = TouchController(sink, geometry)

  @Test fun hiddenKeysAndFieldNeverSendInput() {
    val selected = TouchController(sink, TouchGeometry(600f, 400f, ControlSettings(controlsMask = 2)))
    selected.down(0, 50f, 350f)
    selected.down(1, 300f, 350f)
    selected.down(2, 300f, 150f)
    selected.up(1)
    assertEquals(listOf("D2", "U2"), sink.events)
    assertEquals(0, sink.fieldBegins)
    selected.resize(TouchGeometry(600f, 400f, ControlSettings(controlsMask = 0)))
    sink.events.clear()
    selected.down(3, 150f, 350f)
    selected.down(4, 300f, 150f)
    assertEquals(emptyList(), sink.events)
  }

  @Test fun fieldOnlyUsesLowerPictureAndSignalsOwnershipUntilLastRelease() {
    val selected = TouchController(sink, TouchGeometry(600f, 400f, ControlSettings(controlsMask = 64)))
    selected.down(0, 150f, 350f)
    selected.down(1, 400f, 350f)
    selected.up(1)
    assertEquals(1, sink.fieldBegins)
    assertEquals(0, sink.fieldEnds)
    selected.up(0)
    assertEquals(1, sink.fieldEnds)
  }

  @Test fun laneBoundariesUseHalfOpenIntervals() {
    for (lane in 0..5) {
      assertEquals(lane, geometry.laneAt(lane * 100f, 350f))
      assertEquals(lane, geometry.laneAt(lane * 100f + 99.9f, 350f))
    }
    assertNull(geometry.laneAt(-1f, 350f))
    assertNull(geometry.laneAt(600f, 350f))
    assertNull(geometry.laneAt(100f, 400f))
    assertNull(geometry.laneAt(Float.NaN, 350f))
  }

  @Test fun pointerLocksLaneAcrossMoves() {
    touch.down(7, 250f, 350f)
    touch.move(7, 550f)
    touch.up(7)
    assertEquals(listOf("D3", "U3"), sink.events)
  }

  @Test fun sameLaneReferenceCountsAndDuplicateUp() {
    touch.down(1, 20f, 350f)
    touch.down(9, 80f, 350f)
    touch.up(1)
    assertEquals(1, touch.laneCount(0))
    assertEquals(listOf("D1"), sink.events)
    touch.up(9)
    touch.up(9)
    assertEquals(listOf("D1", "U1"), sink.events)
  }

  @Test fun sixKeysHoldIndependentlyAlongsideField() {
    touch.down(7, 300f, 150f)
    for (lane in 0..5) touch.down(lane, lane * 100f + 50f, 350f)
    touch.move(7, 450f)
    touch.up(3)
    assertEquals(7, touch.fieldPointerId)
    for (lane in 0..5) assertEquals(if (lane == 3) 0 else 1, touch.laneCount(lane))
    assertEquals(listOf("D1", "D2", "D3", "D4", "D5", "D6", "R0.25", "U4"), sink.events)
  }

  @Test fun additionalFieldFingerCannotStealOrBecomeLane() {
    touch.down(3, 150f, 150f)
    touch.down(8, 450f, 150f)
    touch.move(8, 580f)
    touch.up(3)
    touch.move(8, 50f)
    assertEquals(-1, touch.fieldPointerId)
    assertEquals(emptyList(), sink.events)
    touch.up(8)
    touch.down(8, 50f, 350f)
    assertEquals("D1", sink.events.last())
  }

  @Test fun cancelAndReconnectRequireFreshTouches() {
    touch.down(1, 50f, 350f)
    touch.cancel()
    touch.move(1, 250f)
    touch.up(1)
    assertEquals(listOf("D1", "CLEAR"), sink.events)
    touch.down(1, 150f, 350f)
    touch.reset()
    touch.up(1)
    touch.down(1, 150f, 350f)
    assertEquals(listOf("D1", "CLEAR", "D2", "D2"), sink.events)
  }

  @Test fun rapidTapsPreserveEveryTransition() {
    repeat(1000) {
      touch.down(2, 250f, 350f)
      touch.up(2)
    }
    assertEquals(2000, sink.events.size)
    assertEquals(0, touch.laneCount(2))
  }

  @Test fun relativeMovementUsesCalibratedWidthAndClampsEdges() {
    val relative = TouchController(sink, TouchGeometry(600f, 400f,
      ControlSettings(fieldMode = FieldMode.RELATIVE, fieldLeft = 0.25f, fieldRight = 0.75f)))
    relative.down(5, 300f, 150f)
    relative.move(5, 375f)
    relative.move(5, 800f)
    assertEquals(listOf("R0.25", "R0.25"), sink.events)
  }

  @Test fun defaultRelativeTouchDoesNotJumpOnRetouchOrReplayCancelledMotion() {
    touch.down(5, 300f, 150f)
    touch.move(5, 450f)
    touch.up(5)
    touch.down(5, 150f, 150f)
    touch.move(5, 150f)
    touch.move(5, 300f)
    touch.cancel()
    touch.move(5, 500f)
    assertEquals(listOf("R0.25", "R0.25", "CLEAR"), sink.events)
    assertEquals(2, sink.fieldBegins)
  }

  @Test fun experimentalAbsoluteModeStillSendsPositions() {
    val absolute = TouchController(sink, TouchGeometry(600f, 400f, ControlSettings(fieldMode = FieldMode.ABSOLUTE)))
    absolute.down(5, 300f, 150f)
    absolute.move(5, 450f)
    assertEquals(listOf("A0.5", "A0.75"), sink.events)
  }
}
