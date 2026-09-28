package dev.cedarflake.ift.transport

import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HostInteropTest {
  @Test fun kotlinClientDrivesNativeHostAndReconnectsWithoutStaleHolds() {
    val host = System.getenv("IFT_HOST_EXE")
    assumeTrue("Set IFT_HOST_EXE to run the cross-language integration test", !host.isNullOrBlank())
    val port = ServerSocket(0).use { it.localPort }
    val outputRoot = Files.createDirectories(Path.of(System.getProperty("ift.testOutput")))
    val temporary = Files.createTempDirectory(outputRoot, "native-host-").toFile()
    val trace = temporary.resolve("input.txt")
    val log = temporary.resolve("host.log")
    val process = ProcessBuilder(requireNotNull(host), "--dry-run", "--port", port.toString(), "--trace", trace.path)
      .redirectErrorStream(true).redirectOutput(log).start()
    val listener = TestListener()
    val client = ControlClient(listener, Executor { it.run() }, port)
    try {
      awaitCondition {
        check(process.isAlive) { log.readText() }
        try { Socket("127.0.0.1", port).use { true } } catch (_: java.io.IOException) { false }
      }
      client.connect()
      listener.awaitReady()
      val configuration = listener.configurations.poll(2, TimeUnit.SECONDS)
      assertTrue(configuration != null && configuration.peers == 1 && configuration.labels.size == 6)
      for (lane in 1..6) client.send(MessageType.LANE_DOWN, lane)
      client.send(MessageType.FIELD_ABSOLUTE, value = 0.5f)
      awaitCondition { trace.exists() && trace.readLines().contains("ABS 640 360") }
      client.send(MessageType.FIELD_BEGIN)
      client.send(MessageType.FIELD_RELATIVE, value = 1f)
      client.send(MessageType.FIELD_RELATIVE, value = -0.25f)
      awaitCondition { trace.readLines().contains("REL -320") }
      assertEquals(listOf("REL 1280", "REL -320"), trace.readLines().filter { it.startsWith("REL ") })
      client.close()
      listener.awaitState(ConnectionState.DISCONNECTED)
      awaitCondition { trace.readLines().count { it.startsWith("UP ") } == 6 }
      listener.readiness.clear()
      client.connect()
      listener.awaitReady()
      client.send(MessageType.LANE_DOWN, 2)
      client.send(MessageType.LANE_UP, 2)
      awaitCondition { trace.readLines().count { it == "UP 2" } == 2 }
      client.close()
      listener.awaitState(ConnectionState.DISCONNECTED)
      val lines = trace.readLines()
      assertEquals(7, lines.count { it.startsWith("DOWN ") })
      assertEquals(7, lines.count { it.startsWith("UP ") })
    } finally {
      client.close()
      process.destroy()
      assertTrue(process.waitFor(5, TimeUnit.SECONDS))
    }
  }

  private fun awaitCondition(condition: () -> Boolean) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
    while (System.nanoTime() < deadline) {
      if (condition()) return
      Thread.sleep(10)
    }
    error("Integration condition timed out")
  }
}
