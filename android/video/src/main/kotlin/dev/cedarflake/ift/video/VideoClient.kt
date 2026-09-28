package dev.cedarflake.ift.video

import android.view.Surface

import dev.cedarflake.ift.transport.VideoFrameQueue
import dev.cedarflake.ift.transport.VideoPacket
import dev.cedarflake.ift.transport.VideoPacketReader
import dev.cedarflake.ift.transport.VideoPacketType

import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

interface VideoListener {
  fun onStatus(detail: String)
  fun onFormat(width: Int, height: Int)
  fun onStatistics(snapshot: VideoSnapshot)
}

class VideoClient(private val listener: VideoListener, private val executor: Executor) : Closeable {
  private class Session(val surface: Surface, val port: Int) {
    val running = AtomicBoolean(true)
    val socket = AtomicReference<Socket?>()
    fun stop() {
      running.set(false)
      try { socket.getAndSet(null)?.close() } catch (_: IOException) { }
    }
  }

  private val active = AtomicReference<Session?>()

  fun connect(surface: Surface, port: Int = 27183) {
    require(port in 1024..65535)
    close()
    val session = Session(surface, port)
    active.set(session)
    Thread({ run(session) }, "ift-video").start()
  }

  override fun close() { active.getAndSet(null)?.stop() }

  private fun emit(session: Session, action: () -> Unit) {
    executor.execute { if (active.get() === session && session.running.get()) action() }
  }

  private fun run(session: Session) {
    var retry = 0
    while (session.running.get() && session.surface.isValid) {
      try {
        stream(session)
        retry = 0
      } catch (error: Exception) {
        emit(session) { listener.onStatus("Video: ${error.message ?: error.javaClass.simpleName}") }
      } finally {
        try { session.socket.getAndSet(null)?.close() } catch (_: IOException) { }
      }
      val delay = (500L shl retry.coerceAtMost(3))
      retry++
      val deadline = System.nanoTime() + delay * 1_000_000
      while (session.running.get() && System.nanoTime() < deadline) Thread.sleep(50)
    }
  }

  private fun stream(session: Session) {
    val socket = Socket()
    if (!session.running.get()) { socket.close(); return }
    session.socket.set(socket)
    if (!session.running.get()) { socket.close(); return }
    socket.tcpNoDelay = true
    socket.receiveBufferSize = 64 * 1024
    socket.soTimeout = 100
    emit(session) { listener.onStatus("Video connecting over USB") }
    socket.connect(InetSocketAddress("127.0.0.1", session.port), 1500)
    val reader = VideoPacketReader(socket.getInputStream()) { session.running.get() && !socket.isClosed }
    val config = reader.read(idleTimeoutMs = 5000)
    checkError(config)
    require(config.header.type == VideoPacketType.CONFIG && config.header.sequence == 0) { "Video must start with configuration" }
    val queue = VideoFrameQueue()
    val statistics = VideoStatistics()
    val readerFailure = AtomicReference<Exception?>()
    val receiving = AtomicBoolean(true)
    emit(session) { listener.onFormat(config.header.width, config.header.height) }
    SurfaceDecoder(config, session.surface, statistics) { detail ->
      emit(session) { listener.onStatus(detail) }
    }.use { decoder ->
      val receiver = Thread({
        try {
          receiveFrames(reader, config, queue, statistics, session, receiving)
        } catch (error: Exception) {
          readerFailure.set(error)
        } finally {
          receiving.set(false)
        }
      }, "ift-video-receive").apply { start() }
      try {
        while (session.running.get() && receiving.get()) {
          val progressed = decoder.step(queue)
          statistics.snapshot(queue.dropped + decoder.dropped, queue.depth() + decoder.pendingCount())?.let { snapshot ->
            emit(session) { listener.onStatistics(snapshot) }
          }
          if (!progressed) Thread.sleep(1)
        }
        readerFailure.get()?.let { throw it }
      } finally {
        receiving.set(false)
        socket.close()
        receiver.join(1000)
      }
    }
  }

  private fun receiveFrames(
    reader: VideoPacketReader,
    config: VideoPacket,
    queue: VideoFrameQueue,
    statistics: VideoStatistics,
    session: Session,
    receiving: AtomicBoolean,
  ) {
    var sequence = 1
    var lastCapture = 0L
    while (session.running.get() && receiving.get()) {
      val packet = reader.read()
      checkError(packet)
      val header = packet.header
      require(header.type == VideoPacketType.FRAME && header.sequence == sequence++) { "Video sequence discontinuity" }
      require(header.width == config.header.width && header.height == config.header.height && header.fps == config.header.fps) {
        "Video format changed without reconnect"
      }
      require(header.captureTimestamp > lastCapture) { "Reordered video frame" }
      lastCapture = header.captureTimestamp
      statistics.received(packet)
      queue.offer(packet)
    }
  }

  private fun checkError(packet: VideoPacket) {
    if (packet.header.type == VideoPacketType.ERROR) throw IOException(packet.bytes.decodeToString(throwOnInvalidSequence = true))
  }
}
