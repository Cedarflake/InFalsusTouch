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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport

internal class SurfaceDecoder(
  config: VideoPacket,
  surface: Surface,
  private val statistics: VideoStatistics,
  private val describe: (String) -> Unit,
) : Closeable {
  private val worker = Thread.currentThread()
  private val callbackThread = HandlerThread("ift-video-events")
  private val buffers = CodecBufferEvents(::wake)
  private val callbackHandler: Handler
  private val pending = HashMap<Long, FrameTiming>()
  private val rendering = RenderTimings()
  private val releaseClock = FrameReleaseClock(config.header.fps)
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
      callbackHandler = Handler(callbackThread.looper)
      val callback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) = buffers.input(index)
        override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) =
          buffers.output(index, info.presentationTimeUs)
        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) = Unit
        override fun onError(codec: MediaCodec, error: MediaCodec.CodecException) = buffers.failed(error)
      }
      candidate.setCallback(callback, callbackHandler)
      val latencyMode = DecoderLatency.configure(candidate, capabilities, format, surface) {
        candidate.setCallback(callback, callbackHandler)
      }
      if (Build.VERSION.SDK_INT >= 30) surface.setFrameRate(config.header.fps.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
      candidate.setOnFrameRenderedListener({ _, presentationUs, nanoTime ->
        val timing = rendering.take(presentationUs)?.apply { presentTimestamp = nanoTime }
        statistics.presented(timing)
      }, callbackHandler)
      candidate.start()
      codec = candidate
      android.util.Log.i("InFalsusTouchVideo", "Decoder ${decoder.name}, low latency $latencyMode, callback I/O")
      describe("${decoder.name}; low latency $latencyMode")
    } catch (error: Exception) {
      buffers.close()
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
    buffers.checkFailure()
    var progressed = drain()
    if (current == null) current = queue.poll()
    val frame = current
    if (frame != null && pending.size < 2) {
      if (frame.needsFlush) {
        flush()
        dropped += pending.size
        pending.clear()
        rendering.clear()
        releaseClock.reset()
        needsParameters = true
        current = frame.copy(needsFlush = false)
        progressed = true
      }
      val index = buffers.takeInput()
      if (index != null) {
        buffers.useBuffer {
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
        }
        progressed = true
      }
    }
    if (progressed) lastProgress = System.nanoTime()
    if ((pending.isNotEmpty() || current != null) && System.nanoTime() - lastProgress > 1_000_000_000) error("Hardware decoder stalled")
    return progressed
  }

  private fun drain(): Boolean {
    var progressed = false
    repeat(4) {
      val output = buffers.takeOutput() ?: return progressed
      val index = output.index
      val timing = pending.remove(output.presentationUs)
      val now = System.nanoTime()
      if (timing != null) {
        timing.decodeTimestamp = now
        statistics.decoded(timing)
      }
      if (timing != null && now - timing.receiveTimestamp <= 120_000_000) {
        // Spread a decoded burst across display deadlines without growing an unbounded backlog.
        timing.releaseTimestamp = releaseClock.next(System.nanoTime())
        rendering.add(output.presentationUs, timing)
        codec.releaseOutputBuffer(index, timing.releaseTimestamp)
      } else {
        if (timing != null) dropped++
        codec.releaseOutputBuffer(index, false)
      }
      progressed = true
    }
    return progressed
  }

  fun pendingCount(): Int = pending.size + if (current == null) 0 else 1

  fun wake() = LockSupport.unpark(worker)

  fun awaitProgress() = LockSupport.parkNanos(50_000_000)

  private fun flush() {
    buffers.suspendCallbacks()
    codec.flush()
    val drained = CountDownLatch(1)
    // Old buffer callbacks already queued on this looper refer to invalid indices after flush.
    check(callbackHandler.post { buffers.resumeCallbacks(); drained.countDown() })
    check(drained.await(1, TimeUnit.SECONDS)) { "Decoder callback queue stalled during flush" }
    buffers.checkFailure()
    codec.start()
  }

  override fun close() {
    buffers.close()
    try { codec.stop() } finally {
      codec.release()
      callbackThread.quitSafely()
    }
  }
}
