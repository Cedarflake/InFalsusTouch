package dev.cedarflake.ift

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.SurfaceView

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import dev.cedarflake.ift.video.VideoSnapshot
import dev.cedarflake.ift.settings.FieldMode
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

@RunWith(AndroidJUnit4::class)
class VideoDeviceTest {
  @Test fun usbVideoRendersColorsWhileSevenPointersHoldAndReconnects() {
    assumeTrue(InstrumentationRegistry.getArguments().getString("usbVideo") == "true")
    DeviceSettings { it.copy(controlsMask = 127, layoutMode = LayoutMode.OVERLAY, autoConnect = false, autoHideControls = true,
      fieldMode = FieldMode.RELATIVE, fieldLeft = 0f, fieldRight = 1f, fieldHeight = 0.65f, laneHeight = 0.4f,
      videoScale = VideoScale.FIT, highRefreshDisplay = true) }.use { verifyVideo() }
  }

  private fun verifyVideo() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val arguments = InstrumentationRegistry.getArguments()
    val width = arguments.getString("videoWidth", "1280").toInt()
    val height = arguments.getString("videoHeight", "720").toInt()
    val seconds = arguments.getString("videoSeconds", "10").toInt()
    require((width == 1280 && height == 720 || width == 1920 && height == 1080) && seconds in 6..60)
    DeviceActivity.launch().use { scenario ->
      scenario.onActivity { it.toggleConnection() }
      val first = waitForFrames(scenario, 100)
      assertEquals("Received video width", width, first.width)
      assertEquals("Received video height", height, first.height)
      assertTrue("Decoder should make sustained progress", first.presentFps > 20)
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
      val copied = CountDownLatch(1)
      val copyResult = AtomicReference<Int>()
      scenario.onActivity { activity ->
        val surface = activity.findViewById<android.view.ViewGroup>(android.R.id.content).findViewWithTag<SurfaceView>("video-surface")
        PixelCopy.request(surface, bitmap, { result -> copyResult.set(result); copied.countDown() }, Handler(Looper.getMainLooper()))
      }
      assertTrue("PixelCopy timed out", copied.await(3, TimeUnit.SECONDS))
      assertEquals(PixelCopy.SUCCESS, copyResult.get())
      val expected = arrayOf(intArrayOf(235, 60, 60), intArrayOf(60, 210, 90), intArrayOf(60, 90, 235),
        intArrayOf(230, 205, 55), intArrayOf(200, 70, 200), intArrayOf(60, 200, 210))
      for (lane in 0..5) {
        val matches = (1..3).count { sample ->
          val pixel = bitmap.getPixel((lane * width / 6) + sample * width / 24, height / 2)
          val color = expected[lane]
          kotlin.math.abs(Color.red(pixel) - color[0]) < 55 && kotlin.math.abs(Color.green(pixel) - color[1]) < 55 &&
            kotlin.math.abs(Color.blue(pixel) - color[2]) < 55
        }
        assertTrue("Captured/decoded color lane $lane is incorrect", matches >= 2)
      }
      File(instrumentation.targetContext.filesDir, "video-surface.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
      bitmap.recycle()
      val started = SystemClock.uptimeMillis()
      var touchWidth = 0
      scenario.onActivity { activity ->
        val view = activity.findViewById<android.view.ViewGroup>(android.R.id.content).findViewWithTag<ControllerView>("controller")
        touchWidth = view.width
        for (count in 1..7) dispatch(view, count, if (count == 1) MotionEvent.ACTION_DOWN else
          MotionEvent.ACTION_POINTER_DOWN or ((count - 1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), started)
        dispatch(view, 7, MotionEvent.ACTION_MOVE, started, 0.75f)
        dispatch(view, 7, MotionEvent.ACTION_MOVE, started, 0.625f)
      }
      val held = waitForFrames(scenario, first.presentedFrames + seconds * 60, (seconds + 15) * 1000L)
      val steadySeconds = (held.sampleTimestamp - first.sampleTimestamp) / 1e9
      val steadyReceiveFps = (held.receivedFrames - first.receivedFrames) / steadySeconds
      val steadyPresentFps = (held.presentedFrames - first.presentedFrames) / steadySeconds
      assertTrue("Expected sustained ${height}p60 reception, got $steadyReceiveFps", steadyReceiveFps in 58.0..62.0)
      assertTrue("Presentation rate fell below the acceptance bound: $steadyPresentFps", steadyPresentFps in 55.0..63.0)
      scenario.onActivity { it.setPanel("settings") }
      SystemClock.sleep(500)
      run {
        scenario.onActivity { it.setPanel("calibrateField") }
        instrumentation.waitForIdleSync()
        SystemClock.sleep(300)
        scenario.onActivity { activity ->
          val view = activity.findViewById<android.view.ViewGroup>(android.R.id.content).findViewWithTag<ControllerView>("controller")
          for (count in 1..7) dispatch(view, count, if (count == 1) MotionEvent.ACTION_DOWN else
            MotionEvent.ACTION_POINTER_DOWN or ((count - 1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), SystemClock.uptimeMillis())
        }
        instrumentation.uiAutomation.takeScreenshot()?.let { screenshot ->
          File(instrumentation.targetContext.filesDir, "calibration-screen.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
          }
          screenshot.recycle()
        }
        scenario.onActivity { it.setPanel("toolbar") }
      }
      scenario.onActivity { activity ->
        val view = activity.findViewById<android.view.ViewGroup>(android.R.id.content).findViewWithTag<ControllerView>("controller")
        dispatch(view, 7, MotionEvent.ACTION_MOVE, started)
        dispatch(view, 7, MotionEvent.ACTION_CANCEL, started)
      }
      instrumentation.uiAutomation.takeScreenshot()?.let { screenshot ->
        File(instrumentation.targetContext.filesDir, "video-screen.png").outputStream().use {
          screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
      }
      val timing = requireNotNull(held.lastTiming)
      assertTrue(timing.captureTimestamp <= timing.encodeTimestamp && timing.encodeTimestamp <= timing.sendTimestamp)
      assertTrue(timing.receiveTimestamp <= timing.submitTimestamp && timing.submitTimestamp <= timing.decodeTimestamp)
      assertTrue(timing.presentTimestamp >= timing.decodeTimestamp)
      val metrics = JSONObject().put("receiveFps", held.receiveFps).put("decodeFps", held.decodeFps)
        .put("width", held.width).put("height", held.height)
        .put("touchWidth", touchWidth)
        .put("steadySeconds", steadySeconds).put("steadyReceiveFps", steadyReceiveFps).put("steadyPresentFps", steadyPresentFps)
        .put("presentFps", held.presentFps).put("presentedFrames", held.presentedFrames)
        .put("droppedFrames", held.droppedFrames).put("queueDepth", held.queueDepth)
        .put("captureAvailableToEncodeMs", held.captureToEncodeMs).put("decodeMs", held.decoderMs)
        .put("receiveToPresentMs", held.receiveToPresentMs)
      val latency = requireNotNull(held.latency)
      assertEquals("Steady latency window", 240, latency.samples)
      metrics.put("latencyFrames", latency.samples)
        .put("receiveToSubmitMeanMs", latency.receiveToSubmit.meanMs)
        .put("decodeMeanMs", latency.decode.meanMs)
        .put("decodeToPresentMeanMs", latency.decodeToPresent.meanMs)
        .put("receiveToPresentMeanMs", latency.receiveToPresent.meanMs)
        .put("receiveToPresentP95Ms", latency.receiveToPresent.p95Ms)
      scenario.onActivity { activity ->
        val state = activity.uiSnapshot()
        metrics.put("inputRttMs", state["rtt"]).put("decoder", state["videoDetail"]).put("displayHz", state["displayHz"])
      }
      scenario.onActivity { it.toggleConnection() }
      SystemClock.sleep(500)
      scenario.onActivity { assertEquals(null, it.videoSnapshot) }
      scenario.onActivity { it.toggleConnection() }
      val reconnected = waitForFrames(scenario, 60)
      assertEquals(width, reconnected.width)
      assertEquals(height, reconnected.height)
      metrics.put("reconnectPresentedFrames", reconnected.presentedFrames)
      File(instrumentation.targetContext.filesDir, "video-metrics.json").writeText(metrics.toString(2))
    }
  }

  private fun waitForFrames(scenario: DeviceActivity, count: Long, timeoutMs: Long = 20_000): VideoSnapshot {
    val last = AtomicReference<VideoSnapshot?>()
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (SystemClock.uptimeMillis() < deadline) {
      scenario.onActivity { last.set(it.videoSnapshot) }
      val snapshot = last.get()
      if (snapshot != null && snapshot.presentedFrames >= count) return snapshot
      SystemClock.sleep(100)
    }
    error("Video did not present $count frames; last=${last.get()}")
  }

  private fun dispatch(view: ControllerView, count: Int, action: Int, started: Long, fieldX: Float = 0.5f) {
    val properties = Array(count) { index -> MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER } }
    val coordinates = Array(count) { index -> MotionEvent.PointerCoords().apply {
      x = if (index == 0) view.width * fieldX else (index - 0.5f) * view.width / 6f
      y = if (index == 0) view.height * 0.4f else view.height * 0.9f
      pressure = 1f
      size = 1f
    } }
    val event = MotionEvent.obtain(started, SystemClock.uptimeMillis(), action, count, properties, coordinates,
      0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
    try { view.dispatchTouchEvent(event) } finally { event.recycle() }
  }
}
