package dev.cedarflake.ift

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.Choreographer
import android.view.InputDevice
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.View

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import dev.cedarflake.ift.video.VideoSnapshot
import dev.cedarflake.ift.settings.FieldMode
import dev.cedarflake.ift.settings.JudgmentLayout
import dev.cedarflake.ift.settings.LayoutMode
import dev.cedarflake.ift.settings.VideoScale

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class GameVideoDeviceTest {
  @Test fun selectedGameWindowReachesThePhoneWithAlignedLocalFeedback() {
    assumeTrue(InstrumentationRegistry.getArguments().getString("gameVideo") == "true")
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val highRefresh = InstrumentationRegistry.getArguments().getString("highRefresh") != "false"
    DeviceSettings { it.copy(controlsMask = 127, showStatistics = false, autoConnect = false, autoHideControls = true,
      highRefreshDisplay = highRefresh, layoutMode = LayoutMode.ALIGNED, laneHeight = 0.4f, videoScale = VideoScale.FIT,
      fieldMode = FieldMode.RELATIVE, judgment = JudgmentLayout()) }.use {
      DeviceActivity.launch().use { scenario ->
        scenario.onActivity { it.toggleConnection() }
        val first = waitForSample(scenario) { it.presentedFrames >= 100 }
        val cadence = FrameCadence()
        val display = JSONObject()
        scenario.onActivity { cadence.start() }
        val last = try {
          waitForSample(scenario) { it.sampleTimestamp - first.sampleTimestamp >= 10_000_000_000L }
        } finally {
          scenario.onActivity { activity ->
            cadence.stop()
            val current = requireNotNull(activity.window.decorView.display)
            display.put("panelHz", current.mode.refreshRate).put("appRefreshHz", current.refreshRate)
              .put("requestedHz", activity.window.attributes.preferredRefreshRate)
              .put("vsyncCallbackHz", cadence.rate()).put("vsyncCallbacks", cadence.frames)
          }
        }
        val elapsed = (last.sampleTimestamp - first.sampleTimestamp) / 1e9
        val received = (last.receivedFrames - first.receivedFrames) / elapsed
        val presented = (last.presentedFrames - first.presentedFrames) / elapsed
        assertTrue("The selected window did not sustain video", received > 10 && presented > 10)
        val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val copied = CountDownLatch(1)
        val result = AtomicReference<Int>()
        val bounds = Rect()
        scenario.onActivity { activity ->
          val surface = activity.findViewById<ViewGroup>(android.R.id.content).findViewWithTag<SurfaceView>("video-surface")
          val position = IntArray(2)
          surface.getLocationOnScreen(position)
          bounds.set(position[0], position[1], position[0] + surface.width, position[1] + surface.height)
          val visible = Rect()
          assertTrue(surface.getGlobalVisibleRect(visible))
          assertEquals("Fit must not clip the video surface", bounds, visible)
          PixelCopy.request(surface, bitmap, { result.set(it); copied.countDown() }, Handler(Looper.getMainLooper()))
        }
        assertTrue(copied.await(3, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, result.get())
        val colors = mutableSetOf<Int>()
        for (y in 36 until 720 step 72) for (x in 40 until 1280 step 80) colors += bitmap.getPixel(x, y)
        assertTrue("The selected game's decoded frame appears blank", colors.size > 16)
        File(instrumentation.targetContext.filesDir, "game-surface.png").outputStream().use {
          bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
        val layout = JSONObject().put("left", bounds.left).put("top", bounds.top)
          .put("width", bounds.width()).put("height", bounds.height())
        requireNotNull(instrumentation.uiAutomation.takeScreenshot()).let { screenshot ->
          try {
            File(instrumentation.targetContext.filesDir, "game-phone.png").outputStream().use {
              screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            layout.put("screenWidth", screenshot.width).put("screenHeight", screenshot.height)
            assertTrue("Video must be centered on the physical screen", abs(bounds.left + bounds.right - screenshot.width) <= 2 &&
              abs(bounds.top + bounds.bottom - screenshot.height) <= 2)
            assertTrue("Fit must preserve the complete 16:9 frame", abs(bounds.width() - bounds.height() * 16.0 / 9.0) <= 1.0)
          } finally { screenshot.recycle() }
        }
        val started = SystemClock.uptimeMillis()
        try {
          scenario.onActivity { activity ->
            val view = activity.window.decorView
            for (count in 1..7) dispatch(view, bounds, count, if (count == 1) MotionEvent.ACTION_DOWN else
              MotionEvent.ACTION_POINTER_DOWN or ((count - 1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), started)
            dispatch(view, bounds, 7, MotionEvent.ACTION_MOVE, started, 0.715f)
          }
          instrumentation.waitForIdleSync()
          SystemClock.sleep(250)
          requireNotNull(instrumentation.uiAutomation.takeScreenshot()).let { screenshot ->
            try {
              File(instrumentation.targetContext.filesDir, "game-phone-pressed.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
              }
            } finally { screenshot.recycle() }
          }
        } finally {
          scenario.onActivity { activity ->
            val view = activity.window.decorView
            dispatch(view, bounds, 7, MotionEvent.ACTION_CANCEL, started)
          }
        }
        SystemClock.sleep(250)
        val metrics = JSONObject().put("steadySeconds", elapsed).put("receiveFps", received).put("presentFps", presented)
          .put("presentedFrames", last.presentedFrames).put("droppedFrames", last.droppedFrames)
          .put("queueDepth", last.queueDepth).put("megabitsPerSecond", last.megabitsPerSecond)
          .put("captureAvailableToEncodeMs", last.captureToEncodeMs).put("decoderMs", last.decoderMs)
          .put("receiveToPresentMs", last.receiveToPresentMs).put("inputInjection", false)
          .put("syntheticPointers", 7)
          .put("display", display).put("layout", layout)
        val latency = requireNotNull(last.latency)
        metrics.put("latencyFrames", latency.samples)
          .put("receiveToSubmitMeanMs", latency.receiveToSubmit.meanMs)
          .put("decodeMeanMs", latency.decode.meanMs)
          .put("decodeToPresentMeanMs", latency.decodeToPresent.meanMs)
          .put("receiveToPresentMeanMs", latency.receiveToPresent.meanMs)
          .put("receiveToPresentP95Ms", latency.receiveToPresent.p95Ms)
        scenario.onActivity { activity ->
          val state = activity.uiSnapshot()
          metrics.put("inputRttMs", state["rtt"]).put("decoder", state["videoDetail"])
        }
        File(instrumentation.targetContext.filesDir, "game-metrics.json").writeText(metrics.toString(2))
      }
    }
  }

  private fun dispatch(view: View, picture: Rect, count: Int, action: Int, started: Long, fieldX: Float = 0.5f) {
    // Observed hit positions in the 16:9 tutorial, independent of the layout implementation.
    val points = arrayOf(fieldX to 0.4f, 0.13f to 0.64f, 0.28f to 0.64f, 0.43f to 0.64f,
      0.57f to 0.64f, 0.72f to 0.64f, 0.87f to 0.64f)
    val origin = IntArray(2)
    view.getLocationOnScreen(origin)
    val properties = Array(count) { index -> MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER } }
    val coordinates = Array(count) { index -> MotionEvent.PointerCoords().apply {
      x = picture.left - origin[0] + picture.width() * points[index].first
      y = picture.top - origin[1] + picture.height() * points[index].second
      pressure = 1f
      size = 1f
    } }
    val event = MotionEvent.obtain(started, SystemClock.uptimeMillis(), action, count, properties, coordinates,
      0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
    try { view.dispatchTouchEvent(event) } finally { event.recycle() }
  }

  private class FrameCadence : Choreographer.FrameCallback {
    private var first = 0L
    private var last = 0L
    var frames = 0
      private set
    fun start() { Choreographer.getInstance().postFrameCallback(this) }
    fun stop() { Choreographer.getInstance().removeFrameCallback(this) }
    fun rate(): Double = if (last > first) (frames - 1) * 1e9 / (last - first) else 0.0
    override fun doFrame(frameTimeNanos: Long) {
      if (frames++ == 0) first = frameTimeNanos
      last = frameTimeNanos
      Choreographer.getInstance().postFrameCallback(this)
    }
  }

  private fun waitForSample(scenario: DeviceActivity, ready: (VideoSnapshot) -> Boolean): VideoSnapshot {
    val current = AtomicReference<VideoSnapshot?>()
    val deadline = SystemClock.uptimeMillis() + 25_000
    while (SystemClock.uptimeMillis() < deadline) {
      scenario.onActivity { current.set(it.videoSnapshot) }
      current.get()?.let { if (ready(it)) return it }
      SystemClock.sleep(100)
    }
    error("Selected-game video did not reach its sample gate: ${current.get()}")
  }
}
