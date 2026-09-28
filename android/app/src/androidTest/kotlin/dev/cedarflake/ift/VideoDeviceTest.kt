package dev.cedarflake.ift

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.inspector.WindowInspector
import android.widget.Button

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import dev.cedarflake.ift.video.VideoSnapshot

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
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    DeviceActivity.launch().use { scenario ->
      scenario.onActivity { it.findViewById<android.view.ViewGroup>(android.R.id.content).findViewWithTag<Button>("connect").performClick() }
      val first = waitForFrames(scenario, 100)
      assertTrue("Decoder should make sustained progress", first.presentFps > 20)
      val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
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
          val pixel = bitmap.getPixel((lane * 1280 / 6) + sample * 1280 / 24, 360)
          val color = expected[lane]
          kotlin.math.abs(Color.red(pixel) - color[0]) < 55 && kotlin.math.abs(Color.green(pixel) - color[1]) < 55 &&
            kotlin.math.abs(Color.blue(pixel) - color[2]) < 55
        }
        assertTrue("Captured/decoded color lane $lane is incorrect", matches >= 2)
      }
      File(instrumentation.targetContext.filesDir, "video-surface.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
      bitmap.recycle()
      val started = SystemClock.uptimeMillis()
      scenario.onActivity { activity ->
        val view = activity.findViewById<android.view.ViewGroup>(android.R.id.content).findViewWithTag<ControllerView>("controller")
        for (count in 1..7) dispatch(view, count, if (count == 1) MotionEvent.ACTION_DOWN else
          MotionEvent.ACTION_POINTER_DOWN or ((count - 1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), started)
      }
      val held = waitForFrames(scenario, first.presentedFrames + 360)
      val steadySeconds = (held.sampleTimestamp - first.sampleTimestamp) / 1e9
      val steadyReceiveFps = (held.receivedFrames - first.receivedFrames) / steadySeconds
      val steadyPresentFps = (held.presentedFrames - first.presentedFrames) / steadySeconds
      assertTrue("Expected sustained 720p60 reception, got $steadyReceiveFps", steadyReceiveFps in 58.0..62.0)
      assertTrue("Presentation rate fell below the acceptance bound: $steadyPresentFps", steadyPresentFps in 55.0..63.0)
      scenario.onActivity { activity ->
        val content = activity.findViewById<android.view.ViewGroup>(android.R.id.content)
        content.findViewWithTag<Button>("menu").performClick()
        content.findViewWithTag<Button>("settings").performClick()
      }
      SystemClock.sleep(500)
      if (Build.VERSION.SDK_INT >= 29) {
        scenario.onActivity {
          val button = WindowInspector.getGlobalWindowViews()
            .firstNotNullOfOrNull { it.findViewWithTag<Button>("calibrate-touch") }
          requireNotNull(button).performClick()
        }
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
        scenario.onActivity { it.findViewById<android.view.ViewGroup>(android.R.id.content)
          .findViewWithTag<Button>("calibration-cancel").performClick() }
      } else {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        instrumentation.waitForIdleSync()
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
        .put("steadySeconds", steadySeconds).put("steadyReceiveFps", steadyReceiveFps).put("steadyPresentFps", steadyPresentFps)
        .put("presentFps", held.presentFps).put("presentedFrames", held.presentedFrames)
        .put("droppedFrames", held.droppedFrames).put("queueDepth", held.queueDepth)
        .put("captureAvailableToEncodeMs", held.captureToEncodeMs).put("decodeMs", held.decoderMs)
        .put("receiveToPresentMs", held.receiveToPresentMs)
      File(instrumentation.targetContext.filesDir, "video-metrics.json").writeText(metrics.toString(2))
      scenario.onActivity { it.findViewById<android.view.ViewGroup>(android.R.id.content).findViewWithTag<Button>("connect").performClick() }
      SystemClock.sleep(500)
      scenario.onActivity { assertEquals(null, it.videoSnapshot) }
      scenario.onActivity { it.findViewById<android.view.ViewGroup>(android.R.id.content).findViewWithTag<Button>("connect").performClick() }
      assertTrue(waitForFrames(scenario, 60).presentedFrames >= 60)
    }
  }

  private fun waitForFrames(scenario: DeviceActivity, count: Long): VideoSnapshot {
    val last = AtomicReference<VideoSnapshot?>()
    val deadline = SystemClock.uptimeMillis() + 20_000
    while (SystemClock.uptimeMillis() < deadline) {
      scenario.onActivity { last.set(it.videoSnapshot) }
      val snapshot = last.get()
      if (snapshot != null && snapshot.presentedFrames >= count) return snapshot
      SystemClock.sleep(100)
    }
    error("Video did not present $count frames; last=${last.get()}")
  }

  private fun dispatch(view: ControllerView, count: Int, action: Int, started: Long) {
    val properties = Array(count) { index -> MotionEvent.PointerProperties().apply { id = index; toolType = MotionEvent.TOOL_TYPE_FINGER } }
    val coordinates = Array(count) { index -> MotionEvent.PointerCoords().apply {
      x = if (index == 0) view.width / 2f else (index - 0.5f) * view.width / 6f
      y = if (index == 0) view.height * 0.4f else view.height * 0.9f
      pressure = 1f
      size = 1f
    } }
    val event = MotionEvent.obtain(started, SystemClock.uptimeMillis(), action, count, properties, coordinates,
      0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
    try { view.dispatchTouchEvent(event) } finally { event.recycle() }
  }
}
