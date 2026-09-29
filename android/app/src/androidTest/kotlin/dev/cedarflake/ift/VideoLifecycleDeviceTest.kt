package dev.cedarflake.ift

import android.os.SystemClock
import android.system.ErrnoException
import android.system.Os
import android.view.InputDevice
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import dev.cedarflake.ift.settings.FieldMode
import dev.cedarflake.ift.settings.LayoutMode
import dev.cedarflake.ift.settings.VideoScale

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class VideoLifecycleDeviceTest {
  private val inputWorkers = setOf("ift-connect", "ift-ack")
  private val videoWorkers = setOf("ift-video", "ift-video-receive", "ift-video-events")

  @Test fun streamingSurvivesActivityAndSurfaceLifecyclesWithoutReplayingHolds() {
    assumeTrue(InstrumentationRegistry.getArguments().getString("usbVideoLifecycle") == "true")
    DeviceSettings { it.copy(controlsMask = 127, layoutMode = LayoutMode.OVERLAY, autoConnect = false,
      buttonHaptics = false, fieldMode = FieldMode.RELATIVE, fieldLeft = 0f, fieldRight = 1f,
      fieldHeight = 0.65f, laneHeight = 0.4f, videoScale = VideoScale.FIT, highRefreshDisplay = true) }.use {
      verifyLifecycles()
    }
  }

  private fun verifyLifecycles() {
    val arguments = InstrumentationRegistry.getArguments()
    val cycles = arguments.getString("lifecycleCycles", "3").toInt()
    require(cycles in 1..10)
    val metricsFile = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "video-lifecycle-metrics.json")
    val phases = JSONArray()
    val resources = JSONArray()
    val metrics = JSONObject().put("appBuildMode", DeviceBuild.mode()).put("cycles", cycles)
      .put("width", 1280).put("height", 720).put("targetFps", 60)
      .put("phases", phases).put("resources", resources).put("passed", false)
    metricsFile.writeText(metrics.toString(2))
    try {
      assertEquals(arguments.getString("appBuildMode", "profile"), DeviceBuild.mode())
      DeviceActivity.launch().use { scenario ->
        connect(scenario)
        scenario.onActivity { it.toggleConnection() }
        waitForIdle(scenario, emptySet())
        // Warm up framework/Flutter resources before comparing later disconnects.
        resources.put(resourceSnapshot("warmup-disconnected"))
        connect(scenario)
        var holdBatches = 0
        repeat(cycles) { cycle ->
          for (kind in listOf("activity-recreate", "background-resume", "surface-recreate")) {
            val phase = JSONObject().put("cycle", cycle + 1).put("kind", kind).put("completed", false)
            phases.put(phase)
            val started = hold(scenario, metrics)
            holdBatches++
            val transition = SystemClock.uptimeMillis()
            when (kind) {
              "activity-recreate" -> scenario.recreate()
              "background-resume" -> scenario.background()
              else -> scenario.onActivity { surface(it).visibility = View.GONE }
            }
            val remaining = if (kind == "surface-recreate") inputWorkers else emptySet()
            waitForIdle(scenario, remaining)
            phase.put("idleAfterMs", SystemClock.uptimeMillis() - transition)
            resources.put(resourceSnapshot("${cycle + 1}-$kind-idle"))
            val recovery = SystemClock.uptimeMillis()
            when (kind) {
              "background-resume" -> { scenario.resume(); connect(scenario) }
              "activity-recreate" -> connect(scenario)
              else -> {
                scenario.onActivity { surface(it).visibility = View.VISIBLE }
                waitForVideo(scenario)
              }
            }
            phase.put("recoveryTo60FramesMs", SystemClock.uptimeMillis() - recovery)
            resources.put(resourceSnapshot("${cycle + 1}-$kind-playing"))
            // No new DOWN: an old gesture must remain cancelled after recovery.
            scenario.onActivity { activity ->
              val view = controller(activity)
              dispatch(view, 7, MotionEvent.ACTION_MOVE, started, 0.9f)
              endGesture(view, started)
            }
            SystemClock.sleep(250)
            phase.put("completed", true)
            metrics.put("holdBatches", holdBatches)
            metricsFile.writeText(metrics.toString(2))
          }
        }
        val started = hold(scenario, metrics)
        scenario.onActivity { endGesture(controller(it), started) }
        SystemClock.sleep(250)
        metrics.put("holdBatches", holdBatches + 1)
        scenario.onActivity { it.toggleConnection() }
        waitForIdle(scenario, emptySet())
        resources.put(resourceSnapshot("final-disconnected"))
      }
      waitUntil("Owned I/O workers survived Activity finish") { workers().isEmpty() }
      metrics.put("passed", true)
    } catch (failure: Throwable) {
      metrics.put("failure", failure.toString())
      resources.put(resourceSnapshot("failure"))
      throw failure
    } finally {
      metricsFile.writeText(metrics.toString(2))
    }
  }

  private fun connect(scenario: DeviceActivity) {
    scenario.onActivity {
      assertEquals("DISCONNECTED", it.uiSnapshot()["connection"])
      it.toggleConnection()
    }
    waitForVideo(scenario)
  }

  private fun waitForVideo(scenario: DeviceActivity) {
    waitUntil("Video did not resume with input ready and 60 presented frames", 20_000) {
      var ready = false
      scenario.onActivity { activity ->
        val snapshot = activity.videoSnapshot
        val state = activity.uiSnapshot()
        ready = snapshot != null && snapshot.presentedFrames >= 60 && activity.hasWindowFocus() &&
          state["connection"] == "CONNECTED" && state["targetReady"] == true && surface(activity).holder.surface.isValid
        if (ready) {
          assertEquals(1280, snapshot?.width)
          assertEquals(720, snapshot?.height)
          assertEquals(60, snapshot?.targetFps)
        }
      }
      ready
    }
    waitUntil("Expected exactly one worker per active input/video role") {
      workers() == (inputWorkers + videoWorkers).associateWith { 1 }
    }
  }

  private fun waitForIdle(scenario: DeviceActivity, remaining: Set<String>) {
    waitUntil("Video or I/O workers survived lifecycle teardown") {
      var idle = false
      scenario.onActivity {
        idle = it.videoSnapshot == null && it.uiSnapshot()["connection"] ==
          if (remaining.isEmpty()) "DISCONNECTED" else "CONNECTED"
        if (remaining.isNotEmpty()) idle = idle && !surface(it).holder.surface.isValid
      }
      idle && workers() == remaining.associateWith { 1 }
    }
  }

  private fun hold(scenario: DeviceActivity, metrics: JSONObject): Long {
    val started = SystemClock.uptimeMillis()
    scenario.onActivity { activity ->
      val view = controller(activity)
      assertTrue(view.width > 0 && view.height > 0)
      metrics.put("touchWidth", view.width)
      for (count in 1..7) dispatch(view, count, if (count == 1) MotionEvent.ACTION_DOWN else
        MotionEvent.ACTION_POINTER_DOWN or ((count - 1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), started)
      dispatch(view, 7, MotionEvent.ACTION_MOVE, started, 0.625f)
    }
    SystemClock.sleep(300)
    return started
  }

  private fun endGesture(view: ControllerView, started: Long) {
    for (count in 7 downTo 1) dispatch(view, count, if (count == 1) MotionEvent.ACTION_UP else
      MotionEvent.ACTION_POINTER_UP or ((count - 1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), started, 0.625f)
  }

  private fun controller(activity: MainActivity): ControllerView =
    activity.findViewById<ViewGroup>(android.R.id.content).findViewWithTag("controller")

  private fun surface(activity: MainActivity): SurfaceView =
    activity.findViewById<ViewGroup>(android.R.id.content).findViewWithTag("video-surface")

  private fun workers(): Map<String, Int> = Thread.getAllStackTraces().keys
    .filter { it.isAlive && it.name in inputWorkers + videoWorkers }.groupingBy { it.name }.eachCount()

  private fun resourceSnapshot(label: String): JSONObject {
    val descriptors = requireNotNull(File("/proc/self/fd").listFiles())
    val sockets = descriptors.count {
      try { Os.readlink(it.path).startsWith("socket:") } catch (_: ErrnoException) { false }
    }
    val codecThreads = requireNotNull(File("/proc/self/task").listFiles()).mapNotNull {
      try { File(it, "comm").readText().trim() } catch (_: IOException) { null }
    }.filter { it.contains("codec", ignoreCase = true) || it.startsWith("OMX") }
    return JSONObject().put("label", label).put("workers", JSONObject(workers()))
      .put("openFileDescriptors", descriptors.size).put("sockets", sockets)
      .put("codecThreads", JSONArray(codecThreads.sorted()))
  }

  private fun waitUntil(message: String, timeoutMs: Long = 5000, condition: () -> Boolean) {
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (SystemClock.uptimeMillis() < deadline) {
      if (condition()) return
      SystemClock.sleep(50)
    }
    error("$message; workers=${workers()}")
  }

  private fun dispatch(view: ControllerView, count: Int, action: Int, started: Long, fieldX: Float = 0.5f) {
    val properties = Array(count) { index -> MotionEvent.PointerProperties().apply {
      id = index
      toolType = MotionEvent.TOOL_TYPE_FINGER
    } }
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
