package dev.cedarflake.ift.transport

import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

interface ControlListener {
  fun onState(state: ConnectionState, detail: String)
  fun onTargetReady(ready: Boolean)
  fun onRtt(milliseconds: Double)
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
  }

  private val current = AtomicReference<Session?>()
  private val generation = AtomicLong()

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
    require(type != MessageType.HELLO && type != MessageType.ACK && type != MessageType.PING)
    val session = current.get() ?: return
    if (!session.ready.get() && type != MessageType.RELEASE_ALL) return
    if (!session.queue.offer(type, lane, value)) end(session, "Input queue overflow; reconnect required")
  }

  override fun close() {
    current.get()?.let { end(it, "Disconnected") }
  }

  private fun writeInputs(session: Session) {
    val output = session.socket.getOutputStream()
    val codec = PacketCodec()
    val event = OutboundEvent()
    var sequence = 1
    val helloTime = System.nanoTime()
    session.pending.register(sequence, helloTime)
    output.write(codec.encode(MessageType.HELLO, 0, 0, sequence++, 0f, helloTime))
    session.queue.offer(MessageType.RELEASE_ALL)
    while (session.running.get()) {
      val hasEvent = session.queue.takeInto(event, 100)
      if (!session.running.get()) break
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
        session.pending.acknowledge(packet)
        val receivedNs = System.nanoTime()
        session.lastAckNs = receivedNs
        if (!hasHandshake) {
          hasHandshake = true
          notify(session) { listener.onState(ConnectionState.CONNECTED, "USB connected") }
        }
        updateReady(session, packet.status == 0)
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
    if (!ready) session.queue.clear()
    if (session.ready.getAndSet(ready) != ready) {
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
