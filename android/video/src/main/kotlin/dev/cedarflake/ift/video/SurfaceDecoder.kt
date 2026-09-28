package dev.cedarflake.ift.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface

import dev.cedarflake.ift.transport.QueuedVideoFrame
import dev.cedarflake.ift.transport.VideoFrameQueue
import dev.cedarflake.ift.transport.VideoPacket
import dev.cedarflake.ift.transport.VideoProtocol

import java.io.Closeable
import java.nio.ByteBuffer

internal class SurfaceDecoder(
  config: VideoPacket,
  surface: Surface,
  private val statistics: VideoStatistics,
  private val describe: (String) -> Unit,
) : Closeable {
  private val callbackThread = HandlerThread("ift-video-present")
  private val pending = HashMap<Long, FrameTiming>()
  private val rendering = RenderTimings()
  private val outputInfo = MediaCodec.BufferInfo()
  private val codec: MediaCodec
  private val parameters = config.bytes
  private var current: QueuedVideoFrame? = null
  private var needsParameters = false
  private var lastProgress = System.nanoTime()
  var dropped = 0L
    private set

  init {
    val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, config.header.width, config.header.height)
    format.setByteBuffer("csd-0", ByteBuffer.wrap(VideoProtocol.parameterSet(parameters, 7)))
    format.setByteBuffer("csd-1", ByteBuffer.wrap(VideoProtocol.parameterSet(parameters, 8)))
    format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, VideoProtocol.MAX_PAYLOAD_SIZE)
    format.setInteger(MediaFormat.KEY_PRIORITY, 0)
    format.setFloat(MediaFormat.KEY_OPERATING_RATE, config.header.fps.toFloat())
    format.setInteger(MediaFormat.KEY_FRAME_RATE, config.header.fps)
    val decoder = selectDecoder(config)
    val capabilities = decoder.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
    val candidate = MediaCodec.createByCodecName(decoder.name)
    try {
      callbackThread.start()
      val latencyMode = DecoderLatency.configure(candidate, capabilities, format, surface)
      if (Build.VERSION.SDK_INT >= 30) surface.setFrameRate(config.header.fps.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
      candidate.setOnFrameRenderedListener({ _, presentationUs, nanoTime ->
        val timing = rendering.take(presentationUs)?.apply { presentTimestamp = nanoTime }
        statistics.presented(timing)
      }, Handler(callbackThread.looper))
      candidate.start()
      codec = candidate
      android.util.Log.i("InFalsusTouchVideo", "Decoder ${decoder.name}, low latency $latencyMode")
      describe("${decoder.name}; low latency $latencyMode")
    } catch (error: Exception) {
      candidate.release()
      callbackThread.quitSafely()
      throw error
    }
  }

  private fun selectDecoder(config: VideoPacket): MediaCodecInfo {
    return MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
      val hardware = if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated
        else !info.name.startsWith("OMX.google.") && !info.name.startsWith("c2.android.")
      !info.isEncoder && hardware && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) } &&
        info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities
          .areSizeAndRateSupported(config.header.width, config.header.height, config.header.fps.toDouble())
    }.maxByOrNull { info ->
      if (Build.VERSION.SDK_INT >= 30 && info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)
          .isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency)) 1 else 0
    } ?: error("No hardware H.264 decoder supports this video format")
  }

  fun step(queue: VideoFrameQueue): Boolean {
    var progressed = drain()
    if (current == null) current = queue.poll()
    val frame = current
    if (frame != null && pending.size < 2) {
      if (frame.needsFlush) {
        codec.flush()
        dropped += pending.size
        pending.clear()
        rendering.clear()
        needsParameters = true
        current = frame.copy(needsFlush = false)
      }
      val index = codec.dequeueInputBuffer(0)
      if (index >= 0) {
        val bytes = if (needsParameters) parameters else frame.packet.bytes
        val buffer = requireNotNull(codec.getInputBuffer(index)) { "Decoder input buffer missing" }
        require(buffer.capacity() >= bytes.size) { "H.264 access unit exceeds decoder buffer" }
        buffer.clear()
        buffer.put(bytes)
        if (needsParameters) {
          codec.queueInputBuffer(index, 0, bytes.size, 0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG)
          needsParameters = false
        } else {
          val packet = frame.packet
          val header = packet.header
          pending[header.presentationUs] = FrameTiming(header.captureTimestamp, header.encodeTimestamp,
            header.sendTimestamp, packet.receiveTimestamp, System.nanoTime())
          codec.queueInputBuffer(index, 0, bytes.size, header.presentationUs, 0)
          current = null
        }
        progressed = true
      }
    }
    if (progressed) lastProgress = System.nanoTime()
    if (pending.isNotEmpty() && System.nanoTime() - lastProgress > 1_000_000_000) error("Hardware decoder stalled")
    return progressed
  }

  private fun drain(): Boolean {
    var progressed = false
    repeat(4) {
      val index = codec.dequeueOutputBuffer(outputInfo, 0)
      if (index == MediaCodec.INFO_TRY_AGAIN_LATER) return progressed
      if (index >= 0) {
        val timing = pending.remove(outputInfo.presentationTimeUs)
        val now = System.nanoTime()
        if (timing != null) {
          timing.decodeTimestamp = now
          statistics.decoded(timing)
        }
        if (timing != null && now - timing.receiveTimestamp <= 120_000_000) {
          // Preserve the exact local deadline for correlation with SurfaceFlinger frame history.
          timing.releaseTimestamp = System.nanoTime()
          rendering.add(outputInfo.presentationTimeUs, timing)
          codec.releaseOutputBuffer(index, timing.releaseTimestamp)
        } else {
          if (timing != null) dropped++
          codec.releaseOutputBuffer(index, false)
        }
        progressed = true
      }
    }
    return progressed
  }

  fun pendingCount(): Int = pending.size + if (current == null) 0 else 1

  override fun close() {
    try { codec.stop() } finally {
      codec.release()
      callbackThread.quitSafely()
    }
  }
}
