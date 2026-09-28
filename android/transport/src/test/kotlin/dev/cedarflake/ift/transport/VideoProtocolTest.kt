package dev.cedarflake.ift.transport

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoProtocolTest {
  private val idr = byteArrayOf(0, 0, 0, 1, 0x65, 0x45)
  private val predicted = byteArrayOf(0, 0, 0, 1, 0x41, 0x45)

  private fun header(size: Int = 6): ByteArray = ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN)
    .putInt(0x49465631).put(1).put(2).putShort(1).putInt(size).putInt(123)
    .putLong(1_000_000).putLong(2_000_000).putLong(3_000_000).putLong(1000)
    .putShort(1280).putShort(720).putShort(60).putShort(0).putInt(8_000_000).putInt(0).array()

  private fun packet(key: Boolean): VideoPacket {
    val decoded = VideoProtocol.decodeHeader(header()).copy(isKeyFrame = key)
    return VideoPacket(decoded, if (key) idr else predicted, System.nanoTime())
  }

  @Test fun readsFragmentedPacketsAndPreservesPcClock() {
    val bytes = header() + idr
    val input = object : ByteArrayInputStream(bytes) {
      override fun read(buffer: ByteArray, offset: Int, length: Int): Int = super.read(buffer, offset, minOf(length, 3))
    }
    val packet = VideoPacketReader(input) { true }.read()
    assertEquals(123, packet.header.sequence)
    assertEquals(1_000_000, packet.header.captureTimestamp)
    assertEquals(1280, packet.header.width)
    assertEquals(720, packet.header.height)
    assertTrue(VideoProtocol.hasIdr(packet.bytes))
    assertTrue(packet.receiveTimestamp > 0)
  }

  @Test fun rejectsHostAllocationsReservedBytesAndBrokenTimestamps() {
    for (size in listOf(0, -1, VideoProtocol.MAX_PAYLOAD_SIZE + 1)) {
      assertFailsWith<IllegalArgumentException> { VideoProtocol.decodeHeader(header(size)) }
    }
    for (offset in listOf(0, 4, 6, 7, 48, 50, 52, 54, 60)) {
      val bytes = header()
      bytes[offset] = 0xff.toByte()
      assertFailsWith<IllegalArgumentException> { VideoProtocol.decodeHeader(bytes) }
    }
    val bytes = header()
    ByteBuffer.wrap(bytes).putLong(24, 1)
    assertFailsWith<IllegalArgumentException> { VideoProtocol.decodeHeader(bytes) }
    assertFailsWith<EOFException> { VideoPacketReader(ByteArrayInputStream(header() + byteArrayOf(0))) { true }.read() }
  }

  @Test fun overflowDropsDependencyChainUntilIdr() {
    val queue = VideoFrameQueue(2)
    queue.offer(packet(false))
    assertNull(queue.poll())
    queue.offer(packet(true))
    assertFalse(assertNotNull(queue.poll()).needsFlush)
    queue.offer(packet(false))
    queue.offer(packet(false))
    queue.offer(packet(false))
    assertEquals(4, queue.dropped)
    assertEquals(0, queue.depth())
    queue.offer(packet(false))
    assertNull(queue.poll())
    queue.offer(packet(true))
    assertTrue(assertNotNull(queue.poll()).needsFlush)
    queue.offer(packet(false))
    assertFalse(assertNotNull(queue.poll()).needsFlush)
  }

  @Test fun extractsAnnexBParameterSetsAndRejectsFalseIdr() {
    val data = byteArrayOf(0, 0, 1, 0x67, 0x42, 0, 0, 0, 1, 0x68, 0x12)
    assertEquals(listOf<Byte>(0, 0, 0, 1, 0x67, 0x42), VideoProtocol.parameterSet(data, 7).toList())
    assertEquals(listOf<Byte>(0, 0, 0, 1, 0x68, 0x12), VideoProtocol.parameterSet(data, 8).toList())
    val queue = VideoFrameQueue()
    assertFailsWith<IllegalArgumentException> { queue.offer(packet(true).copy(bytes = predicted)) }
    assertFailsWith<IllegalArgumentException> { VideoProtocol.hasIdr(byteArrayOf(0, 0, 1)) }
  }
}
