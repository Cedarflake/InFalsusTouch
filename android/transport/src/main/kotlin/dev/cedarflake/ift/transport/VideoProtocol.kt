package dev.cedarflake.ift.transport

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class VideoPacketType(val code: Int) { CONFIG(1), FRAME(2), ERROR(3) }

data class VideoHeader(
  val type: VideoPacketType,
  val isKeyFrame: Boolean,
  val payloadSize: Int,
  val sequence: Int,
  val captureTimestamp: Long,
  val encodeTimestamp: Long,
  val sendTimestamp: Long,
  val presentationUs: Long,
  val width: Int,
  val height: Int,
  val fps: Int,
  val bitrate: Int,
)

data class VideoPacket(val header: VideoHeader, val bytes: ByteArray, val receiveTimestamp: Long)

object VideoProtocol {
  const val VERSION = 2
  const val HEADER_SIZE = 64
  const val MAX_PAYLOAD_SIZE = 4 * 1024 * 1024

  fun subscription(fps: Int): ByteArray {
    require(fps == 0 || fps in 24..120) { "Unsupported requested video FPS" }
    return ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
      .putInt(0x49465631).put(VERSION.toByte()).put(0).putShort(fps.toShort()).array()
  }

  fun decodeHeader(bytes: ByteArray): VideoHeader {
    require(bytes.size == HEADER_SIZE) { "Video header must be 64 bytes" }
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
    require(buffer.int == 0x49465631 && buffer.get().toInt() == VERSION) { "Unsupported video protocol" }
    val code = buffer.get().toInt()
    val type = VideoPacketType.entries.firstOrNull { it.code == code } ?: error("Unknown video packet type")
    val flags = buffer.short.toInt() and 0xffff
    require(flags in 0..1 && (type == VideoPacketType.FRAME || flags == 0)) { "Invalid video flags" }
    val size = buffer.int
    require(size in 1..MAX_PAYLOAD_SIZE) { "Video payload exceeds limit" }
    require(type != VideoPacketType.CONFIG || size <= 65536) { "Video configuration exceeds limit" }
    require(type != VideoPacketType.ERROR || size <= 4096) { "Video diagnostic exceeds limit" }
    val sequence = buffer.int
    val capture = buffer.long
    val encode = buffer.long
    val send = buffer.long
    val pts = buffer.long
    val width = buffer.short.toInt() and 0xffff
    val height = buffer.short.toInt() and 0xffff
    val fps = buffer.short.toInt() and 0xffff
    require(buffer.short.toInt() == 0) { "Video reserved field is set" }
    val bitrate = buffer.int
    require(buffer.int == 0) { "Video reserved field is set" }
    require(width in 128..1920 && width % 2 == 0 && height in 128..1080 && height % 2 == 0 &&
      fps in 24..120 && bitrate in 500_000..40_000_000) { "Unsupported video format" }
    if (type == VideoPacketType.FRAME) {
      require(capture > 0 && encode >= capture && send >= encode && pts == capture / 1000) { "Invalid PC timestamps" }
    }
    return VideoHeader(type, flags == 1, size, sequence, capture, encode, send, pts, width, height, fps, bitrate)
  }

  fun parameterSet(bytes: ByteArray, type: Int): ByteArray {
    val units = nalUnits(bytes)
    val unit = units.firstOrNull { (bytes[it.first].toInt() and 31) == type }
      ?: error("H.264 parameter set $type is missing")
    return byteArrayOf(0, 0, 0, 1) + bytes.copyOfRange(unit.first, unit.last + 1)
  }

  fun hasIdr(bytes: ByteArray): Boolean = nalUnits(bytes).any { (bytes[it.first].toInt() and 31) == 5 }

  private fun prefix(bytes: ByteArray, offset: Int): Int {
    if (offset + 3 > bytes.size || bytes[offset].toInt() != 0 || bytes[offset + 1].toInt() != 0) return 0
    if (bytes[offset + 2].toInt() == 1) return 3
    return if (offset + 4 <= bytes.size && bytes[offset + 2].toInt() == 0 && bytes[offset + 3].toInt() == 1) 4 else 0
  }

  private fun nalUnits(bytes: ByteArray): List<IntRange> {
    val units = ArrayList<IntRange>()
    var offset = 0
    while (offset < bytes.size) {
      val prefixLength = prefix(bytes, offset)
      require(prefixLength > 0) { "H.264 Annex B start code missing" }
      val start = offset + prefixLength
      var end = start
      while (end < bytes.size && prefix(bytes, end) == 0) end++
      require(end > start && bytes[start].toInt() and 0x80 == 0) { "Invalid H.264 NAL" }
      units.add(start until end)
      offset = end
    }
    return units
  }
}

class VideoPacketReader(private val input: InputStream, private val isRunning: () -> Boolean) {
  private val headerBytes = ByteArray(VideoProtocol.HEADER_SIZE)
  private var packetStarted = 0L
  private var idleDeadline = 0L

  fun read(idleTimeoutMs: Long = 0): VideoPacket {
    packetStarted = 0
    idleDeadline = if (idleTimeoutMs > 0) System.nanoTime() + idleTimeoutMs * 1_000_000 else 0
    readExact(headerBytes)
    val header = VideoProtocol.decodeHeader(headerBytes)
    val payload = ByteArray(header.payloadSize)
    readExact(payload)
    return VideoPacket(header, payload, System.nanoTime())
  }

  private fun readExact(bytes: ByteArray) {
    var offset = 0
    while (offset < bytes.size) {
      if (!isRunning()) throw IOException("Video stopped")
      if (packetStarted == 0L && idleDeadline != 0L && System.nanoTime() > idleDeadline) {
        throw IOException("Video configuration timeout")
      }
      if (packetStarted != 0L && System.nanoTime() - packetStarted > 500_000_000) {
        throw IOException("Partial video packet exceeded 500 ms")
      }
      val count = try { input.read(bytes, offset, bytes.size - offset) } catch (_: SocketTimeoutException) { continue }
      if (count < 0) throw EOFException("Video host disconnected")
      if (count == 0) continue
      if (packetStarted == 0L) packetStarted = System.nanoTime()
      offset += count
    }
  }
}
