package dev.cedarflake.ift

import android.os.SystemClock
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.cedarflake.ift.touch.TouchSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MotionEventDeviceTest {
  private var gestureStart = 0L
  private class RecordingSink : TouchSink {
    val events = mutableListOf<String>()
    var fieldStarts = 0
    var fieldEnds = 0
    override fun laneDown(lane: Int) { events += "D$lane" }
    override fun laneUp(lane: Int) { events += "U$lane" }
    override fun fieldAbsolute(x: Float) { events += "A$x" }
    override fun fieldRelative(deltaX: Float) { events += "R$deltaX" }
    override fun releaseAll() { events += "CLEAR" }
    override fun fieldBegin() { fieldStarts++ }
    override fun fieldEnd() { fieldEnds++ }
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

  private fun dispatch(view: View?, action: Int, fingers: List<Finger>) {
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
    try {
      if (view == null) InstrumentationRegistry.getInstrumentation().sendPointerSync(event)
      else view.dispatchTouchEvent(event)
    } finally { event.recycle() }
  }

  @Test fun heldGroundKeySurvivesShortTapsThroughThePhoneWindow() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    DeviceSettings { it.copy(autoConnect = false) }.use {
      DeviceActivity.launch().use { scenario ->
        val sink = RecordingSink()
        lateinit var view: ControllerView
        scenario.onActivity { activity ->
          val original = activity.findViewById<View>(android.R.id.content).findViewWithTag<ControllerView>("controller")
          val root = original.parent as ControllerRoot
          view = ControllerView(activity, sink)
          root.addView(view, root.indexOfChild(original), FrameLayout.LayoutParams(-1, -1))
          root.removeView(original)
          root.gameplay = view
          view.setInputAllowed(true)
        }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(700)
        val origin = IntArray(2)
        var width = 0
        var height = 0
        scenario.onActivity {
          view.getLocationOnScreen(origin)
          width = view.width
          height = view.height
          sink.events.clear()
        }
        val held = Finger(0, origin[0] + width * 0.4f, origin[1] + height * 0.9f)
        val tapped = Finger(1, origin[0] + width * 0.6f, held.y)
        val pointerDown = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        val pointerUp = MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        try {
          dispatch(null, MotionEvent.ACTION_DOWN, listOf(held))
          SystemClock.sleep(900)
          repeat(3) {
            dispatch(null, pointerDown, listOf(held, tapped))
            SystemClock.sleep(65)
            dispatch(null, pointerUp, listOf(held, tapped))
            SystemClock.sleep(100)
          }
          scenario.onActivity {
            assertEquals(listOf("D3", "D4", "U4", "D4", "U4", "D4", "U4"), sink.events)
            assertTrue(view.createAccessibilityNodeInfo().contentDescription.isNotBlank())
          }
          dispatch(null, MotionEvent.ACTION_UP, listOf(held))
          scenario.onActivity { assertEquals("U3", sink.events.last()) }
        } finally {
          dispatch(null, MotionEvent.ACTION_CANCEL, listOf(held))
        }
      }
    }
  }

  @Test fun twoGroundKeysStayHeldWhileThirdFingerSlidesAndRetouchesField() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    instrumentation.runOnMainSync {
      val sink = RecordingSink()
      val root = ControllerRoot(instrumentation.targetContext)
      val view = ControllerView(instrumentation.targetContext, sink)
      var interfaceTouches = 0
      val overlay = object : View(instrumentation.targetContext) {
        override fun onTouchEvent(event: MotionEvent): Boolean { interfaceTouches++; return true }
      }
      root.addView(view, FrameLayout.LayoutParams(-1, -1))
      root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
      root.gameplay = view
      root.interfaceView = overlay
      root.interfaceRegions = listOf(RectF(0f, 0f, 60f, 60f))
      root.layout(0, 0, 600, 400)
      view.layout(0, 0, 600, 400)
      view.setInputAllowed(true)
      sink.events.clear()
      val left = Finger(12, 150f, 350f)
      val right = Finger(3, 450f, 350f)
      var field = Finger(7, 300f, 150f)
      dispatch(root, MotionEvent.ACTION_DOWN, listOf(left))
      dispatch(root, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(left, right))
      dispatch(root, MotionEvent.ACTION_POINTER_DOWN or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(left, right, field))
      for (x in listOf(450f, 60f, 30f)) {
        field = field.copy(x = x, y = 30f)
        dispatch(root, MotionEvent.ACTION_MOVE, listOf(field, right, left))
      }
      dispatch(root, MotionEvent.ACTION_POINTER_UP, listOf(field, right, left))
      assertEquals(listOf("D2", "D5", "R150.0", "R-390.0", "R-30.0"), sink.events)
      assertEquals(1, sink.fieldEnds)
      field = Finger(21, 240f, 150f)
      dispatch(root, MotionEvent.ACTION_POINTER_DOWN or (2 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(left, right, field))
      field = field.copy(x = 360f)
      dispatch(root, MotionEvent.ACTION_MOVE, listOf(field, right, left))
      dispatch(root, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(field, right, left))
      dispatch(root, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(field, left))
      dispatch(root, MotionEvent.ACTION_UP, listOf(field.copy(x = 400f)))
      assertEquals(listOf("D2", "D5", "R150.0", "R-390.0", "R-30.0", "R120.0", "U5", "U2", "R40.0"), sink.events)
      assertEquals(2, sink.fieldStarts)
      assertEquals(2, sink.fieldEnds)
      assertEquals(0, interfaceTouches)
    }
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
    assertEquals(listOf("D1", "D2", "D3", "D4", "D5", "D6", "R150.0", "CLEAR"), sink.events)
  }

  @Test fun fieldReleaseIncludesItsFinalPositionWithoutJumpingOnRetouch() = withView { view, sink ->
    dispatch(view, MotionEvent.ACTION_DOWN, listOf(Finger(7, 300f, 150f)))
    dispatch(view, MotionEvent.ACTION_MOVE, listOf(Finger(7, 450f, 150f)))
    dispatch(view, MotionEvent.ACTION_UP, listOf(Finger(7, 455f, 150f)))
    dispatch(view, MotionEvent.ACTION_DOWN, listOf(Finger(7, 120f, 150f)))
    dispatch(view, MotionEvent.ACTION_UP, listOf(Finger(7, 125f, 150f)))
    assertEquals(listOf("R150.0", "R5.0", "R5.0"), sink.events)
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

  @Test fun localFeedbackStaysLitUntilTheLastFingerLifts() = withView { view, _ ->
    val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
    fun pixel(): Int {
      view.draw(Canvas(bitmap))
      return bitmap.getPixel(250, 315)
    }
    try {
      val resting = pixel()
      val first = Finger(1, 250f, 350f)
      val second = Finger(2, 260f, 350f)
      dispatch(view, MotionEvent.ACTION_DOWN, listOf(first))
      val pressed = pixel()
      assertNotEquals("Local pressed feedback must be visible without a Host", resting, pressed)
      dispatch(view, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), listOf(first, second))
      dispatch(view, MotionEvent.ACTION_POINTER_UP, listOf(first, second))
      assertEquals(pressed, pixel())
      dispatch(view, MotionEvent.ACTION_UP, listOf(second))
      assertEquals(resting, pixel())
    } finally { bitmap.recycle() }
  }
}
