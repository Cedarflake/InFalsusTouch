package dev.cedarflake.ift

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.cedarflake.ift.touch.TouchSink
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MotionEventDeviceTest {
  private var gestureStart = 0L
  private class RecordingSink : TouchSink {
    val events = mutableListOf<String>()
    override fun laneDown(lane: Int) { events += "D$lane" }
    override fun laneUp(lane: Int) { events += "U$lane" }
    override fun fieldAbsolute(x: Float) { events += "A$x" }
    override fun fieldRelative(deltaX: Float) { events += "R$deltaX" }
    override fun releaseAll() { events += "CLEAR" }
  }

  private data class Finger(val id: Int, val x: Float, val y: Float)

  private fun withView(test: (ControllerView, RecordingSink) -> Unit) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.runOnMainSync {
      val sink = RecordingSink()
      val view = ControllerView(instrumentation.targetContext, sink)
      FrameLayout(instrumentation.targetContext).addView(view)
      view.layout(0, 0, 600, 400)
      view.setInputAllowed(true)
      sink.events.clear()
      test(view, sink)
    }
  }

  private fun dispatch(view: ControllerView, action: Int, fingers: List<Finger>) {
    val properties = fingers.map { finger ->
      MotionEvent.PointerProperties().apply { id = finger.id; toolType = MotionEvent.TOOL_TYPE_FINGER }
    }.toTypedArray()
    val coordinates = fingers.map { finger ->
      MotionEvent.PointerCoords().apply { x = finger.x; y = finger.y; pressure = 1f; size = 1f }
    }.toTypedArray()
    val now = SystemClock.uptimeMillis()
    if (action == MotionEvent.ACTION_DOWN) gestureStart = now
    val event = MotionEvent.obtain(gestureStart, now, action, fingers.size, properties, coordinates,
      0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
    try { view.dispatchTouchEvent(event) } finally { event.recycle() }
  }

  @Test fun nativePointerIndicesMayReorderWithoutChangingOwnership() = withView { view, sink ->
    val fingers = mutableListOf(Finger(7, 300f, 150f))
    dispatch(view, MotionEvent.ACTION_DOWN, fingers)
    val ids = listOf(10, 3, 15, 2, 21, 4)
    for (lane in 0..5) {
      fingers += Finger(ids[lane], lane * 100f + 50f, 350f)
      dispatch(view, MotionEvent.ACTION_POINTER_DOWN or (fingers.lastIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), fingers)
    }
    val reordered = fingers.reversed().map { if (it.id == 7) it.copy(x = 450f) else it.copy(x = 590f) }
    dispatch(view, MotionEvent.ACTION_MOVE, reordered)
    dispatch(view, MotionEvent.ACTION_CANCEL, reordered)
    assertEquals(listOf("A0.5", "D1", "D2", "D3", "D4", "D5", "D6", "A0.75", "CLEAR"), sink.events)
  }

  @Test fun nativePointerUpUsesActionIndexAndReferenceCounts() = withView { view, sink ->
    val first = Finger(7, 250f, 350f)
    val second = Finger(12, 260f, 350f)
    dispatch(view, MotionEvent.ACTION_DOWN, listOf(first))
    dispatch(view, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(first, second))
    dispatch(view, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(second, first))
    assertEquals(listOf("D3"), sink.events)
    dispatch(view, MotionEvent.ACTION_UP, listOf(second))
    assertEquals(listOf("D3", "U3"), sink.events)
  }

  @Test fun disabledOrResizedViewReleasesWithoutReplayingOldTouch() = withView { view, sink ->
    dispatch(view, MotionEvent.ACTION_DOWN, listOf(Finger(1, 150f, 350f)))
    view.setInputAllowed(false)
    view.setInputAllowed(true)
    dispatch(view, MotionEvent.ACTION_MOVE, listOf(Finger(1, 250f, 350f)))
    dispatch(view, MotionEvent.ACTION_UP, listOf(Finger(1, 250f, 350f)))
    dispatch(view, MotionEvent.ACTION_DOWN, listOf(Finger(1, 550f, 350f)))
    view.layout(0, 0, 800, 400)
    assertEquals(listOf("D2", "CLEAR", "D6", "CLEAR"), sink.events)
  }
}
