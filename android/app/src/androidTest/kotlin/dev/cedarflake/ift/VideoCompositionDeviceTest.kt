package dev.cedarflake.ift

import android.app.Instrumentation
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.Surface
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import dev.cedarflake.ift.settings.LayoutMode
import dev.cedarflake.ift.settings.VideoScale
import dev.cedarflake.ift.video.FrameTiming
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
  private data class Phase(
    val name: String,
    val flutterVisible: Boolean = true,
    val controllerVisible: Boolean = true,
    val frameRate: Float,
    val compatibility: Int = Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
  )

  @Test fun isolatesVisibleOverlaysWithinOneVideoSession() {
    assumeTrue(InstrumentationRegistry.getArguments().getString("videoComposition") == "true")
    compare(listOf(
      Phase("normal-before", frameRate = 60f),
      Phase("native-controls", flutterVisible = false, frameRate = 60f),
      Phase("video-only", flutterVisible = false, controllerVisible = false, frameRate = 60f),
      Phase("normal-after", frameRate = 60f),
    ), "composition", 60)
  }

  @Test fun comparesSurfaceFrameRateHintsWithinOneVideoSession() {
    assumeTrue(InstrumentationRegistry.getArguments().getString("videoSurfaceHints") == "true")
    assumeTrue(Build.VERSION.SDK_INT >= 30)
    val fps = InstrumentationRegistry.getArguments().getString("videoFps", "60").toInt()
    require(fps in setOf(60, 90, 120))
    compare(listOf(
      Phase("fixed-before", frameRate = fps.toFloat()),
      Phase("unrestricted", frameRate = fps.toFloat(), compatibility = Surface.FRAME_RATE_COMPATIBILITY_DEFAULT),
      Phase("fixed-middle", frameRate = fps.toFloat()),
      Phase("unspecified", frameRate = 0f, compatibility = Surface.FRAME_RATE_COMPATIBILITY_DEFAULT),
      Phase("fixed-after", frameRate = fps.toFloat()),
    ), "surface-hints", fps)
  }

  private fun compare(phases: List<Phase>, probe: String, fps: Int) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val resultFile = File(instrumentation.targetContext.filesDir, "video-$probe.json")
    for (name in listOf(resultFile.name) + phases.map { "$probe-${it.name}.png" }) {
      val file = File(instrumentation.targetContext.filesDir, name)
      check(!file.exists() || file.delete()) { "Cannot clear previous composition evidence" }
    }
    val buildMode = DeviceBuild.mode()
    assertEquals(InstrumentationRegistry.getArguments().getString("appBuildMode", "debug"), buildMode)
    val samples = JSONArray()
    val result = JSONObject().put("streamFps", fps).put("inputInjection", false).put("phases", samples)
      .put("appBuildMode", buildMode)
    DeviceSettings { it.copy(controlsMask = 127, autoConnect = false, autoHideControls = false,
      showStatistics = false, highRefreshDisplay = true, layoutMode = LayoutMode.OVERLAY,
      videoScale = VideoScale.FIT) }.use {
      DeviceActivity.launch().use { scenario ->
        lateinit var flutter: FlutterView
        lateinit var controller: ControllerView
        lateinit var surfaceView: SurfaceView
        var flutterVisibility = View.VISIBLE
        var controllerVisibility = View.VISIBLE
        scenario.onActivity { activity ->
          val root = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ControllerRoot
          flutter = (0 until root.childCount).map { root.getChildAt(it) }.filterIsInstance<FlutterView>().single()
          controller = root.findViewWithTag("controller")
          surfaceView = root.findViewWithTag("video-surface")
          flutterVisibility = flutter.visibility
          controllerVisibility = controller.visibility
          activity.toggleConnection()
        }
        try {
          var first = waitForSample(scenario) { it.presentedFrames >= 120 }
          assertEquals(fps, first.targetFps)
          for (phase in phases) {
            scenario.onActivity {
              flutter.visibility = if (phase.flutterVisible) View.VISIBLE else View.INVISIBLE
              controller.visibility = if (phase.controllerVisible) View.VISIBLE else View.INVISIBLE
              if (Build.VERSION.SDK_INT >= 30) surfaceView.holder.surface.setFrameRate(phase.frameRate, phase.compatibility)
            }
            val changedAt = System.nanoTime()
            // Exclude policy transitions and recovery after the preceding screenshot/dumpsys.
            first = waitForSample(scenario) { it.sampleTimestamp - changedAt >= 5_000_000_000L }
            val last = waitForSample(scenario) { it.sampleTimestamp - first.sampleTimestamp >= 8_000_000_000L }
            assertTrue("Video session restarted during ${phase.name}", last.receivedFrames > first.receivedFrames)
            val seconds = (last.sampleTimestamp - first.sampleTimestamp) / 1e9
            val latency = requireNotNull(last.latency)
            assertEquals(240, latency.samples)
            val sample = JSONObject().put("name", phase.name).put("seconds", seconds)
              .put("settlingSeconds", (first.sampleTimestamp - changedAt) / 1e9)
              .put("surfaceFrameRate", phase.frameRate).put("surfaceCompatibility", phase.compatibility)
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
              assertEquals(phase.flutterVisible, flutter.isShown)
              assertEquals(phase.controllerVisible, controller.isShown)
              val display = requireNotNull(activity.window.decorView.display)
              sample.put("panelHz", display.mode.refreshRate).put("appRefreshHz", display.refreshRate)
                .put("flutterVisible", flutter.isShown).put("controllerVisible", controller.isShown)
              result.put("decoder", activity.uiSnapshot()["videoDetail"])
            }
            sample.put("compositor", compositorSample(instrumentation, requireNotNull(last.lastTiming)))
            samples.put(sample)
            resultFile.writeText(result.toString(2))
            requireNotNull(instrumentation.uiAutomation.takeScreenshot()).let { screenshot ->
              try {
                File(instrumentation.targetContext.filesDir, "$probe-${phase.name}.png").outputStream().use {
                  screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
              } finally { screenshot.recycle() }
            }
          }
        } finally {
          scenario.onActivity {
            flutter.visibility = flutterVisibility
            controller.visibility = controllerVisibility
            val surface = surfaceView.holder.surface
            if (Build.VERSION.SDK_INT >= 30 && surface.isValid) {
              surface.setFrameRate(fps.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
            }
          }
        }
      }
    }
    for (index in 0 until samples.length()) {
      val sample = samples.getJSONObject(index)
      assertTrue("Reception fell below the bound in ${sample.getString("name")}", sample.getDouble("receiveFps") >= fps * 58.0 / 60)
      assertTrue("Presentation fell below the bound in ${sample.getString("name")}", sample.getDouble("presentFps") >= fps * 55.0 / 60)
    }
  }

  private fun compositorSample(instrumentation: Instrumentation, timing: FrameTiming): JSONObject {
    val sample = JSONObject().put("receiveNs", timing.receiveTimestamp).put("decodeNs", timing.decodeTimestamp)
      .put("releaseNs", timing.releaseTimestamp).put("callbackPresentNs", timing.presentTimestamp)
    try {
      fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command),
      ).bufferedReader().use { it.readText() }

      val prefix = "SurfaceView[${instrumentation.targetContext.packageName}/"
      val layer = shell("dumpsys SurfaceFlinger --list").lineSequence()
        .single { it.startsWith(prefix) && it.contains("(BLAST)") }
      require(layer.none { it.isWhitespace() })
      // UiAutomation executes the command directly; quotes become part of an argument.
      val raw = shell("dumpsys SurfaceFlinger --latency $layer")
      sample.put("layer", layer).put("frameHistory", raw)
      val rows = raw.lineSequence().drop(1).mapNotNull { line ->
        val columns = line.trim().split(Regex("\\s+")).mapNotNull { it.toLongOrNull() }
        columns.takeIf { it.size == 3 && it.all { value -> value > 0 && value < Long.MAX_VALUE } }
      }.toList()
      require(rows.isNotEmpty()) { "SurfaceFlinger returned no completed frame records" }
      sample.put("exactCallbackTimestampMatch", rows.any { it[1] == timing.presentTimestamp })
      val match = rows.singleOrNull { it[0] == timing.releaseTimestamp }
      sample.put("exactReleaseTimestampMatch", match != null)
      if (match != null) {
        sample.put("desiredPresentNs", match[0]).put("actualPresentNs", match[1]).put("readyNs", match[2])
          .put("decodeToReadyMs", (match[2] - timing.decodeTimestamp) / 1e6)
          .put("readyToPresentMs", (match[1] - match[2]) / 1e6)
          .put("receiveToCompositorPresentMs", (match[1] - timing.receiveTimestamp) / 1e6)
          .put("callbackTimestampMinusCompositorMs", (timing.presentTimestamp - match[1]) / 1e6)
      }
    } catch (error: Exception) {
      sample.put("error", error.message ?: error.javaClass.simpleName)
    }
    return sample
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
