package dev.cedarflake.ift.transport

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class MessageType(val wireValue: Int) {
  HELLO(1), LANE_DOWN(2), LANE_UP(3), FIELD_ABSOLUTE(4), FIELD_RELATIVE(5),
  RELEASE_ALL(6), PING(7), FIELD_BEGIN(8), FIELD_END(9), ASSIGN_CONTROLS(10), VIDEO_FRAME_RATE(11), ACK(128), CONFIGURATION(129),
}

data class Packet(
  val type: MessageType,
  val lane: Int = 0,
  val status: Int = 0,
  val sequence: Int = 1,
  val value: Float = 0f,
  val timestampNs: Long = 0,
)

class ProtocolException(message: String) : IOException(message)

class PacketCodec {
  private val buffer = ByteBuffer.allocate(PACKET_SIZE).order(ByteOrder.BIG_ENDIAN)

  fun encode(packet: Packet): ByteArray = encode(
    packet.type, packet.lane, packet.status, packet.sequence, packet.value, packet.timestampNs,
  )

  // Returned storage belongs to this codec and is reused on the next encode.
  fun encode(type: MessageType, lane: Int, status: Int, sequence: Int, value: Float, timestampNs: Long): ByteArray {
    validate(type, lane, status, value)
    buffer.clear()
    buffer.putInt(MAGIC)
    buffer.put(3)
    buffer.put(type.wireValue.toByte())
    buffer.put(lane.toByte())
    buffer.put(status.toByte())
    buffer.putInt(sequence)
    buffer.putFloat(value)
    buffer.putLong(timestampNs)
    buffer.putLong(0)
    return buffer.array()
  }

  fun decode(bytes: ByteArray): Packet {
    if (bytes.size != PACKET_SIZE) throw ProtocolException("Incorrect packet size")
    buffer.clear()
    buffer.put(bytes)
    buffer.flip()
    if (buffer.int != MAGIC || buffer.get().toInt() != 3) throw ProtocolException("Unsupported magic/version; update both Host and app")
    val code = buffer.get().toInt() and 0xff
    val type = MessageType.entries.firstOrNull { it.wireValue == code }
      ?: throw ProtocolException("Unknown message type")
    val lane = buffer.get().toInt() and 0xff
    val status = buffer.get().toInt() and 0xff
    val sequence = buffer.int
    val value = buffer.float
    val timestamp = buffer.long
    if (buffer.long != 0L) throw ProtocolException("Reserved bytes must be zero")
    validate(type, lane, status, value)
    if (type == MessageType.CONFIGURATION && (sequence == 0 || timestamp ushr 48 != 0L ||
        (0 until 6).any { (timestamp ushr (it * 8) and 255) !in 1L..105L })) throw ProtocolException("Invalid binding configuration")
    return Packet(type, lane, status, sequence, value, timestamp)
  }

  private fun validate(type: MessageType, lane: Int, status: Int, value: Float) {
    if (type == MessageType.CONFIGURATION) {
      if (lane !in 0..127 || status !in 0..2 || !value.isFinite() || value !in 1f..7f || value != value.toInt().toFloat()) {
        throw ProtocolException("Invalid controller configuration")
      }
      return
    }
    val isLane = type == MessageType.LANE_DOWN || type == MessageType.LANE_UP
    if ((isLane && lane !in 1..6) || (!isLane && lane != 0)) throw ProtocolException("Invalid lane")
    if ((status != 0 && status != 1 && status != 2 && status != 4) || (type != MessageType.ACK && status != 0)) throw ProtocolException("Invalid status")
    val validValue = when (type) {
      MessageType.FIELD_ABSOLUTE -> value in 0f..1f
      MessageType.FIELD_RELATIVE -> value in -1f..1f
      MessageType.ASSIGN_CONTROLS -> value in 0f..127f && value == value.toInt().toFloat()
      MessageType.HELLO -> value == 0f || value in 24f..120f && value == value.toInt().toFloat()
      MessageType.VIDEO_FRAME_RATE -> value in 24f..120f && value == value.toInt().toFloat()
      else -> value == 0f
    }
    if (!value.isFinite() || !validValue) throw ProtocolException("Invalid Field value")
  }

  companion object {
    const val PACKET_SIZE = 32
    private const val MAGIC = 0x49465431
  }
}
