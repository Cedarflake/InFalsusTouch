package dev.cedarflake.ift.transport

import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

interface ControlListener {
  fun onState(state: ConnectionState, detail: String)
  fun onTargetReady(ready: Boolean)
  fun onRtt(milliseconds: Double)
  fun onConfiguration(configuration: ControllerConfiguration) {}
  fun onFieldStatus(status: Int) {}
}

class ControlClient(
  private val listener: ControlListener,
  private val callbacks: Executor,
  private val port: Int = 27184,
) : AutoCloseable {
  private class Session(val generation: Long) {
    val socket = Socket()
    val queue = InputQueue()
    val pending = PendingAcks()
    val running = AtomicBoolean(true)
    val ready = AtomicBoolean(false)
    @Volatile var lastAckNs = System.nanoTime()
    var configuration: ControllerConfiguration? = null
  }

  private val current = AtomicReference<Session?>()
  private val generation = AtomicLong()
  private val desiredControls = AtomicInteger(127)
  private val desiredVideoFps = AtomicInteger(0)

  fun setVideoFrameRate(fps: Int) {
    require(fps in 24..120)
    desiredVideoFps.set(fps)
  }

  fun setControls(mask: Int) {
    require(mask in 0..127)
    if (desiredControls.getAndSet(mask) == mask) return
    current.get()?.let { session ->
      if (!session.queue.offer(MessageType.ASSIGN_CONTROLS, value = mask.toFloat())) end(session, "Input queue overflow")
    }
  }

  @Synchronized fun connect() {
    if (current.get() != null) return
    val session = Session(generation.incrementAndGet())
    current.set(session)
    notify(session) { listener.onState(ConnectionState.CONNECTING, "Connecting over USB…") }
    worker("ift-connect") {
      try {
        session.socket.tcpNoDelay = true
        session.socket.soTimeout = 100
        session.socket.connect(InetSocketAddress("127.0.0.1", port), 1500)
        if (!session.running.get()) return@worker
        session.lastAckNs = System.nanoTime()
        worker("ift-ack") { readAcks(session) }
        writeInputs(session)
      } catch (error: Exception) {
        end(session, error.message ?: "Connection failed")
      }
    }
  }

  fun send(type: MessageType, lane: Int = 0, value: Float = 0f) {
    require(type != MessageType.HELLO && type != MessageType.ACK && type != MessageType.CONFIGURATION &&
      type != MessageType.PING && type != MessageType.VIDEO_FRAME_RATE)
    val session = current.get() ?: return
    if (!session.ready.get() && type != MessageType.RELEASE_ALL && type != MessageType.ASSIGN_CONTROLS) return
    if (!session.queue.offer(type, lane, value)) end(session, "Input queue overflow; reconnect required")
  }

  fun sendRelativePixels(deltaX: Float) {
    forEachRelativeStep(deltaX) { send(MessageType.FIELD_RELATIVE, value = it) }
  }

  override fun close() {
    current.get()?.let { end(it, "Disconnected") }
  }

  private fun writeInputs(session: Session) {
    val output = session.socket.getOutputStream()
    val codec = PacketCodec()
    val event = OutboundEvent()
    var sequence = 1
    var sentVideoFps = desiredVideoFps.get()
    val helloTime = System.nanoTime()
    session.pending.register(sequence, helloTime)
    output.write(codec.encode(MessageType.HELLO, 0, 0, sequence++, sentVideoFps.toFloat(), helloTime))
    session.queue.offer(MessageType.RELEASE_ALL)
    while (session.running.get()) {
      val hasEvent = session.queue.takeInto(event, 100)
      if (!session.running.get()) break
      val videoFps = desiredVideoFps.get()
      if (videoFps != sentVideoFps) {
        val timestamp = System.nanoTime()
        session.pending.register(sequence, timestamp)
        output.write(codec.encode(MessageType.VIDEO_FRAME_RATE, 0, 0, sequence++, videoFps.toFloat(), timestamp))
        sentVideoFps = videoFps
      }
      if (!hasEvent) {
        event.type = if (session.ready.get()) MessageType.PING else MessageType.RELEASE_ALL
        event.lane = 0
        event.value = 0f
        event.timestampNs = System.nanoTime()
      }
      session.pending.register(sequence, event.timestampNs)
      output.write(codec.encode(event.type, event.lane, 0, sequence++, event.value, event.timestampNs))
    }
  }

  private fun readAcks(session: Session) {
    try {
      val input = session.socket.getInputStream()
      val codec = PacketCodec()
      val bytes = ByteArray(PacketCodec.PACKET_SIZE)
      var offset = 0
      var partialStart = 0L
      var hasHandshake = false
      var lastRttReport = 0L
      var lastFieldStatus = -1
      while (session.running.get()) {
        val now = System.nanoTime()
        if (now - session.lastAckNs > 750_000_000L) throw SocketTimeoutException("Host ACK timeout")
        if (offset > 0 && now - partialStart > 500_000_000L) throw SocketTimeoutException("Partial ACK timeout")
        val count = try {
          input.read(bytes, offset, bytes.size - offset)
        } catch (_: SocketTimeoutException) {
          continue
        }
        if (count < 0) throw EOFException("Host disconnected")
        if (offset == 0) partialStart = System.nanoTime()
        offset += count
        if (offset != bytes.size) continue
        val packet = codec.decode(bytes)
        if (packet.type == MessageType.CONFIGURATION) {
          val configuration = ControllerConfiguration.decode(packet)
          val previous = session.configuration
          val changed = previous == null || previous.controls != configuration.controls ||
            previous.keys != configuration.keys || previous.bindingStatus != configuration.bindingStatus
          session.configuration = configuration
          if (changed) {
            updateReady(session, false)
            if (configuration.controls != desiredControls.get()) session.queue.offer(MessageType.ASSIGN_CONTROLS, value = desiredControls.get().toFloat())
            session.queue.offer(MessageType.RELEASE_ALL)
          }
          notify(session) { listener.onConfiguration(configuration) }
          offset = 0
          continue
        }
        session.pending.acknowledge(packet)
        val receivedNs = System.nanoTime()
        session.lastAckNs = receivedNs
        if (!hasHandshake) {
          hasHandshake = true
          notify(session) { listener.onState(ConnectionState.CONNECTED, "USB connected") }
        }
        updateReady(session, packet.status != 1)
        if (lastFieldStatus != packet.status) {
          lastFieldStatus = packet.status
          notify(session) { listener.onFieldStatus(packet.status) }
        }
        if (receivedNs - lastRttReport > 250_000_000L) {
          val milliseconds = (receivedNs - packet.timestampNs) / 1_000_000.0
          notify(session) { listener.onRtt(milliseconds) }
          lastRttReport = receivedNs
        }
        offset = 0
      }
    } catch (error: Exception) {
      end(session, error.message ?: "Host receive failed")
    }
  }

  private fun updateReady(session: Session, ready: Boolean) {
    val previous = session.ready.getAndSet(ready)
    if (previous && !ready) session.queue.clear()
    if (previous != ready) {
      notify(session) { listener.onTargetReady(ready) }
    }
  }

  private fun end(session: Session, reason: String) {
    if (!session.running.compareAndSet(true, false)) return
    session.ready.set(false)
    session.queue.close()
    try {
      session.socket.close()
    } catch (_: IOException) {
      // Closing an already broken socket does not change the disconnect outcome.
    }
    current.compareAndSet(session, null)
    notify(session, allowClosed = true) {
      listener.onTargetReady(false)
      listener.onState(ConnectionState.DISCONNECTED, reason)
    }
  }

  private fun notify(session: Session, allowClosed: Boolean = false, callback: () -> Unit) {
    callbacks.execute {
      if (generation.get() == session.generation && (allowClosed || session.running.get())) callback()
    }
  }

  private fun worker(name: String, task: () -> Unit) {
    Thread(task, name).apply { isDaemon = true }.start()
  }
}
