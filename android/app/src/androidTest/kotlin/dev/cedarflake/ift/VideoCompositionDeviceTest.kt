package dev.cedarflake.ift

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import dev.cedarflake.ift.settings.LayoutMode
import dev.cedarflake.ift.settings.VideoScale
import dev.cedarflake.ift.video.VideoSnapshot

import io.flutter.embedding.android.FlutterView

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class VideoCompositionDeviceTest {
  @Test fun isolatesVisibleOverlaysWithinOneVideoSession() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    assumeTrue(InstrumentationRegistry.getArguments().getString("videoComposition") == "true")
    val phases = listOf("normal-before", "native-controls", "video-only", "normal-after")
    val resultFile = File(instrumentation.targetContext.filesDir, "video-composition.json")
    for (name in listOf(resultFile.name) + phases.map { "composition-$it.png" }) {
      val file = File(instrumentation.targetContext.filesDir, name)
      check(!file.exists() || file.delete()) { "Cannot clear previous composition evidence" }
    }
    val samples = JSONArray()
    val result = JSONObject().put("streamFps", 60).put("inputInjection", false).put("phases", samples)
    DeviceSettings { it.copy(controlsMask = 127, autoConnect = false, autoHideControls = false,
      showStatistics = false, highRefreshDisplay = true, layoutMode = LayoutMode.OVERLAY,
      videoScale = VideoScale.FIT) }.use {
      DeviceActivity.launch().use { scenario ->
        lateinit var flutter: FlutterView
        lateinit var controller: ControllerView
        var flutterVisibility = View.VISIBLE
        var controllerVisibility = View.VISIBLE
        scenario.onActivity { activity ->
          val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ControllerRoot
          flutter = (0 until root.childCount).map { root.getChildAt(it) }.filterIsInstance<FlutterView>().single()
          controller = root.findViewWithTag("controller")
          flutterVisibility = flutter.visibility
          controllerVisibility = controller.visibility
          activity.toggleConnection()
        }
        try {
          var first = waitForSample(scenario) { it.presentedFrames >= 120 }
          assertEquals(60, first.targetFps)
          for (phase in phases) {
            val flutterVisible = phase.startsWith("normal")
            val controllerVisible = phase != "video-only"
            scenario.onActivity {
              flutter.visibility = if (flutterVisible) View.VISIBLE else View.INVISIBLE
              controller.visibility = if (controllerVisible) View.VISIBLE else View.INVISIBLE
            }
            val last = waitForSample(scenario) { it.sampleTimestamp - first.sampleTimestamp >= 8_000_000_000L }
            assertTrue("Video session restarted during $phase", last.receivedFrames > first.receivedFrames)
            val seconds = (last.sampleTimestamp - first.sampleTimestamp) / 1e9
            val latency = requireNotNull(last.latency)
            assertEquals(240, latency.samples)
            val sample = JSONObject().put("name", phase).put("seconds", seconds)
              .put("receiveFps", (last.receivedFrames - first.receivedFrames) / seconds)
              .put("decodeFps", (last.decodedFrames - first.decodedFrames) / seconds)
              .put("presentFps", (last.presentedFrames - first.presentedFrames) / seconds)
              .put("droppedFrames", last.droppedFrames - first.droppedFrames)
              .put("untrackedPresentedFrames", last.untrackedPresentedFrames - first.untrackedPresentedFrames)
              .put("queueDepth", last.queueDepth).put("latencyFrames", latency.samples)
              .put("receiveToSubmitMeanMs", latency.receiveToSubmit.meanMs)
              .put("decodeMeanMs", latency.decode.meanMs)
              .put("decodeToPresentMeanMs", latency.decodeToPresent.meanMs)
              .put("receiveToPresentMeanMs", latency.receiveToPresent.meanMs)
              .put("receiveToPresentP95Ms", latency.receiveToPresent.p95Ms)
            scenario.onActivity { activity ->
              assertEquals(flutterVisible, flutter.isShown)
              assertEquals(controllerVisible, controller.isShown)
              val display = requireNotNull(activity.window.decorView.display)
              sample.put("panelHz", display.mode.refreshRate).put("appRefreshHz", display.refreshRate)
                .put("flutterVisible", flutter.isShown).put("controllerVisible", controller.isShown)
              result.put("decoder", activity.uiSnapshot()["videoDetail"])
            }
            samples.put(sample)
            resultFile.writeText(result.toString(2))
            requireNotNull(instrumentation.uiAutomation.takeScreenshot()).let { screenshot ->
              try {
                File(instrumentation.targetContext.filesDir, "composition-$phase.png").outputStream().use {
                  screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
              } finally { screenshot.recycle() }
            }
            first = last
          }
        } finally {
          scenario.onActivity {
            flutter.visibility = flutterVisibility
            controller.visibility = controllerVisibility
          }
        }
      }
    }
    for (index in 0 until samples.length()) {
      val sample = samples.getJSONObject(index)
      assertTrue("Reception fell below 58 FPS in ${sample.getString("name")}", sample.getDouble("receiveFps") >= 58)
      assertTrue("Presentation fell below 55 FPS in ${sample.getString("name")}", sample.getDouble("presentFps") >= 55)
    }
  }

  private fun waitForSample(scenario: DeviceActivity, ready: (VideoSnapshot) -> Boolean): VideoSnapshot {
    val last = AtomicReference<VideoSnapshot?>()
    val deadline = SystemClock.uptimeMillis() + 20_000
    while (SystemClock.uptimeMillis() < deadline) {
      scenario.onActivity { last.set(it.videoSnapshot) }
      last.get()?.let { if (ready(it)) return it }
      SystemClock.sleep(100)
    }
    error("Composition sample timed out; last=${last.get()}")
  }
}
