package dev.cedarflake.ift.transport

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProtocolTest {
  @Test fun sharedGoldenVectorsRoundTrip() {
    val codec = PacketCodec()
    var count = 0
    File(System.getProperty("ift.protocolVectors")).forEachLine { line ->
      if (line.isNotBlank() && !line.startsWith("#")) {
        val (name, hex) = line.split(" ")
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val packet = codec.decode(bytes)
        assertContentEquals(bytes, codec.encode(packet), name)
        if (name == "absoluteHalf") assertEquals(0.5f, packet.value)
        if (name == "hello") assertEquals(0x0102030405060708L, packet.timestampNs)
        count++
      }
    }
    assertEquals(14, count)
  }

  @Test fun phoneFrameRatesAreValidatedAndOldVersionsRejected() {
    val codec = PacketCodec()
    for (type in listOf(MessageType.HELLO, MessageType.VIDEO_FRAME_RATE)) {
      for (fps in listOf(24f, 60f, 90f, 120f)) {
        assertEquals(fps, codec.decode(codec.encode(Packet(type, value = fps))).value)
      }
      for (fps in listOf(23f, 121f, 60.5f, Float.NaN)) {
        assertFailsWith<ProtocolException> { codec.encode(Packet(type, value = fps)) }
      }
    }
    assertFailsWith<ProtocolException> { codec.encode(Packet(MessageType.VIDEO_FRAME_RATE)) }
    val old = codec.encode(Packet(MessageType.HELLO)).copyOf().apply { this[4] = 2 }
    assertFailsWith<ProtocolException> { codec.decode(old) }
  }

  @Test fun malformedFramesFailClosed() {
    val codec = PacketCodec()
    val valid = codec.encode(Packet(MessageType.HELLO)).copyOf()
    for (length in 0 until PacketCodec.PACKET_SIZE) {
      assertFailsWith<ProtocolException> { codec.decode(valid.copyOf(length)) }
    }
    for (offset in listOf(0, 4, 5, 6, 7, 24, 31)) {
      val bad = valid.copyOf()
      bad[offset] = 0xff.toByte()
      assertFailsWith<ProtocolException> { codec.decode(bad) }
    }
    for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, -0.1f, 1.1f)) {
      assertFailsWith<ProtocolException> { codec.encode(Packet(MessageType.FIELD_ABSOLUTE, value = value)) }
    }
    assertFailsWith<ProtocolException> { codec.encode(Packet(MessageType.LANE_DOWN, lane = 0)) }
    assertFailsWith<ProtocolException> { codec.encode(Packet(MessageType.LANE_UP, lane = 7)) }
  }

  @Test fun acknowledgmentsMustMatchSentSequenceAndTimestamp() {
    val pending = PendingAcks(2)
    pending.register(-1, 500)
    pending.register(0, 600)
    assertFailsWith<ProtocolException> { pending.register(1, 700) }
    assertFailsWith<ProtocolException> { pending.acknowledge(Packet(MessageType.ACK, sequence = -1, timestampNs = 501)) }
    pending.acknowledge(Packet(MessageType.ACK, sequence = -1, timestampNs = 500))
    pending.acknowledge(Packet(MessageType.ACK, sequence = 0, timestampNs = 600))
    assertFailsWith<ProtocolException> { pending.acknowledge(Packet(MessageType.ACK, sequence = 0, timestampNs = 600)) }
  }
}
