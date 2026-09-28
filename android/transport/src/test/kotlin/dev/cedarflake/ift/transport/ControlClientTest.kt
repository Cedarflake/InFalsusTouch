package dev.cedarflake.ift.transport

import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

internal class TestListener : ControlListener {
  val states = LinkedBlockingQueue<Pair<ConnectionState, String>>()
  val readiness = LinkedBlockingQueue<Boolean>()
  val configurations = LinkedBlockingQueue<ControllerConfiguration>()
  override fun onConfiguration(configuration: ControllerConfiguration) { configurations.put(configuration) }
  override fun onState(state: ConnectionState, detail: String) { states.put(state to detail) }
  override fun onTargetReady(ready: Boolean) { readiness.put(ready) }
  override fun onRtt(milliseconds: Double) { assertTrue(milliseconds >= 0) }

  fun awaitState(expected: ConnectionState): String {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (System.nanoTime() < deadline) {
      val event = states.poll(100, TimeUnit.MILLISECONDS) ?: continue
      if (event.first == expected) return event.second
    }
    error("Did not observe $expected")
  }

  fun awaitReady() {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (System.nanoTime() < deadline) {
      if (readiness.poll(100, TimeUnit.MILLISECONDS) == true) return
    }
    error("Target never became ready")
  }
}

class ControlClientTest {
  private val direct = Executor { it.run() }

  @Test fun stalledHostClosesConnectionWithoutBlockingCaller() {
    ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
      val reader = Thread {
        server.accept().use { socket ->
          val input = socket.getInputStream()
          while (input.read() >= 0) { /* Wait for the client's ACK watchdog to close. */ }
        }
      }.apply { isDaemon = true; start() }
      val listener = TestListener()
      ControlClient(listener, direct, server.localPort).use { client ->
        val start = System.nanoTime()
        client.connect()
        assertTrue(System.nanoTime() - start < TimeUnit.MILLISECONDS.toNanos(500))
        assertTrue(listener.awaitState(ConnectionState.DISCONNECTED).contains("ACK timeout"))
      }
      reader.join(2000)
      assertTrue(!reader.isAlive)
    }
  }

  @Test fun malformedAckIsRejected() {
    ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
      val reader = Thread {
        server.accept().use { socket ->
          DataInputStream(socket.getInputStream()).readFully(ByteArray(32))
          socket.getOutputStream().write(ByteArray(32))
        }
      }.apply { isDaemon = true; start() }
      val listener = TestListener()
      ControlClient(listener, direct, server.localPort).use { client ->
        client.connect()
        assertTrue(listener.awaitState(ConnectionState.DISCONNECTED).contains("magic/version"))
      }
      reader.join(2000)
      assertTrue(!reader.isAlive)
    }
  }

  @Test fun fragmentedAcksCompleteHandshakeAndCloseCleanly() {
    ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
      val received = LinkedBlockingQueue<Packet>()
      val reader = Thread {
        server.accept().use { socket ->
          val input = DataInputStream(socket.getInputStream())
          val codec = PacketCodec()
          val bytes = ByteArray(32)
          repeat(2) { index ->
            input.readFully(bytes)
            val request = codec.decode(bytes)
            received.put(request)
            val ack = codec.encode(Packet(MessageType.ACK, status = if (index == 0) 1 else 0,
              sequence = request.sequence, timestampNs = request.timestampNs))
            for (offset in ack.indices step 4) socket.getOutputStream().write(ack, offset, 4)
          }
          while (input.read() >= 0) { /* Observe client-initiated EOF. */ }
        }
      }.apply { isDaemon = true; start() }
      val listener = TestListener()
      ControlClient(listener, direct, server.localPort).use { client ->
        client.connect()
        listener.awaitReady()
        assertEquals(MessageType.HELLO, assertNotNull(received.poll(1, TimeUnit.SECONDS)).type)
        assertEquals(MessageType.RELEASE_ALL, assertNotNull(received.poll(1, TimeUnit.SECONDS)).type)
      }
      reader.join(2000)
      assertTrue(!reader.isAlive)
    }
  }
}
