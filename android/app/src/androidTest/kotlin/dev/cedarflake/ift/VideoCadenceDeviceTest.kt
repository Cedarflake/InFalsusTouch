package dev.cedarflake.ift

import android.os.SystemClock

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import dev.cedarflake.ift.video.VideoSnapshot

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
class VideoCadenceDeviceTest {
  @Test fun measuresSustainedCadenceWithoutSyntheticInput() {
    val args = InstrumentationRegistry.getArguments()
    assumeTrue(args.getString("videoCadence") == "true")
    val fps = args.getString("videoFps", "120").toInt()
    val seconds = args.getString("videoSeconds", "30").toInt()
    require(fps in 24..120 && seconds in 10..120)
    val output = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "video-cadence.json")
    output.delete()
    DeviceSettings { it.copy(autoConnect = false, highRefreshDisplay = true) }.use {
      DeviceActivity.launch().use { scenario ->
        scenario.onActivity { it.toggleConnection() }
        val current = AtomicReference<VideoSnapshot?>()
        val samples = JSONArray()
        var first: VideoSnapshot? = null
        var last: VideoSnapshot? = null
        val deadline = SystemClock.uptimeMillis() + (seconds + 20) * 1000L
        while (SystemClock.uptimeMillis() < deadline) {
          scenario.onActivity { current.set(it.videoSnapshot) }
          val sample = current.get()
          if (sample != null && sample.receivedFrames >= fps * 3 && sample.sampleTimestamp != last?.sampleTimestamp) {
            if (first == null) first = sample
            last = sample
            samples.put(JSONObject().put("timestampNs", sample.sampleTimestamp)
              .put("receiveFps", sample.receiveFps).put("decodeFps", sample.decodeFps).put("presentFps", sample.presentFps)
              .put("receiveGapMs", sample.maxReceiveGapMs).put("decodeGapMs", sample.maxDecodeGapMs)
              .put("presentGapMs", sample.maxPresentGapMs).put("dropped", sample.droppedFrames)
              .put("recoveries", sample.recoveries).put("queue", sample.queueDepth)
              .put("latencyMeanMs", sample.latency?.receiveToPresent?.meanMs)
              .put("latencyP95Ms", sample.latency?.receiveToPresent?.p95Ms))
            if (sample.sampleTimestamp - first.sampleTimestamp >= seconds * 1_000_000_000L) break
          }
          SystemClock.sleep(100)
        }
        val start = requireNotNull(first) { "No video samples arrived" }
        val end = requireNotNull(last)
        val elapsed = (end.sampleTimestamp - start.sampleTimestamp) / 1e9
        output.writeText(JSONObject().put("fps", fps).put("seconds", elapsed).put("samples", samples)
          .put("receiveFps", (end.receivedFrames - start.receivedFrames) / elapsed)
          .put("decodeFps", (end.decodedFrames - start.decodedFrames) / elapsed)
          .put("presentFps", (end.presentedFrames - start.presentedFrames) / elapsed)
          .put("dropped", end.droppedFrames - start.droppedFrames)
          .put("recoveries", end.recoveries - start.recoveries).toString(2))
        assertEquals(fps, end.targetFps)
        assertTrue("The video stopped before the requested observation completed", elapsed >= seconds)
      }
    }
  }
}
