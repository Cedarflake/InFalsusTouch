package dev.cedarflake.ift.video

import dev.cedarflake.ift.transport.VideoPacket

data class FrameTiming(
  val captureTimestamp: Long,
  val encodeTimestamp: Long,
  val sendTimestamp: Long,
  val receiveTimestamp: Long,
  val submitTimestamp: Long,
  var decodeTimestamp: Long = 0,
  var presentTimestamp: Long = 0,
  var releaseTimestamp: Long = 0,
)

data class VideoSnapshot(
  val receiveFps: Double,
  val decodeFps: Double,
  val presentFps: Double,
  val megabitsPerSecond: Double,
  val droppedFrames: Long,
  val queueDepth: Int,
  val receivedFrames: Long,
  val decodedFrames: Long,
  val presentedFrames: Long,
  val captureToEncodeMs: Double,
  val decoderMs: Double,
  val receiveToPresentMs: Double,
  val lastTiming: FrameTiming?,
  val sampleTimestamp: Long,
  val width: Int,
  val height: Int,
  val latency: VideoLatencySnapshot?,
  val targetFps: Int,
  val untrackedPresentedFrames: Long,
)

internal class VideoStatistics(private val nanoTime: () -> Long = System::nanoTime) {
  private var received = 0L
  private var decoded = 0L
  private var presented = 0L
  private var untrackedPresented = 0L
  private var bytes = 0L
  private var previousReceived = 0L
  private var previousDecoded = 0L
  private var previousPresented = 0L
  private var previousBytes = 0L
  private var lastReport = nanoTime()
  private var encodeMs = 0.0
  private var decodeMs = 0.0
  private var presentMs = 0.0
  private var lastTiming: FrameTiming? = null
  private var width = 0
  private var height = 0
  private var targetFps = 0
  private val latency = VideoLatencyWindow()

  @Synchronized
  fun received(packet: VideoPacket) {
    received++
    bytes += packet.bytes.size
    width = packet.header.width
    height = packet.header.height
    targetFps = packet.header.fps
    encodeMs = (packet.header.encodeTimestamp - packet.header.captureTimestamp) / 1e6
  }

  @Synchronized
  fun decoded(timing: FrameTiming) {
    decoded++
    decodeMs = (timing.decodeTimestamp - timing.submitTimestamp) / 1e6
  }

  @Synchronized
  fun presented(timing: FrameTiming?) {
    presented++
    if (timing == null) {
      untrackedPresented++
      return
    }
    presentMs = (timing.presentTimestamp - timing.receiveTimestamp) / 1e6
    lastTiming = timing.copy()
    latency.record(timing)
  }

  @Synchronized
  fun snapshot(dropped: Long, queued: Int): VideoSnapshot? {
    val now = nanoTime()
    val seconds = (now - lastReport) / 1e9
    if (seconds < 1.0) return null
    val snapshot = VideoSnapshot(
      (received - previousReceived) / seconds, (decoded - previousDecoded) / seconds,
      (presented - previousPresented) / seconds, (bytes - previousBytes) * 8 / seconds / 1e6,
      dropped, queued, received, decoded, presented, encodeMs, decodeMs, presentMs, lastTiming, now, width, height, latency.snapshot(), targetFps,
      untrackedPresented,
    )
    previousReceived = received
    previousDecoded = decoded
    previousPresented = presented
    previousBytes = bytes
    lastReport = now
    return snapshot
  }
}
