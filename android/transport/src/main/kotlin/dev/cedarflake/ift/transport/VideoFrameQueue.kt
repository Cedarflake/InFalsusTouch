package dev.cedarflake.ift.transport

data class QueuedVideoFrame(val packet: VideoPacket, val needsFlush: Boolean)

class VideoFrameQueue(private val capacity: Int = 8, private val maxQueuedNs: Long = 50_000_000) {
  private val frames = ArrayDeque<QueuedVideoFrame>()
  private var isWaitingForIdr = true
  private var needsFlush = false
  @Volatile var dropped: Long = 0
    private set
  @Volatile var recoveries: Long = 0
    private set

  init { require(capacity in 1..8 && maxQueuedNs > 0) }

  @Synchronized
  fun offer(packet: VideoPacket) {
    require(packet.header.type == VideoPacketType.FRAME)
    val oldest = frames.firstOrNull()?.packet
    // A brief USB burst must not turn into a half-GOP freeze by discarding reference frames.
    val isLate = oldest != null && (
      packet.receiveTimestamp - oldest.receiveTimestamp > maxQueuedNs ||
      packet.header.captureTimestamp - oldest.header.captureTimestamp > maxQueuedNs)
    if (frames.size >= capacity || isLate) discardUntilIdr()
    if (isWaitingForIdr) {
      if (!packet.header.isKeyFrame) {
        dropped++
        return
      }
      require(VideoProtocol.hasIdr(packet.bytes)) { "Key-frame flag without IDR" }
      isWaitingForIdr = false
    }
    frames.addLast(QueuedVideoFrame(packet, needsFlush))
    needsFlush = false
  }

  @Synchronized
  fun poll(): QueuedVideoFrame? = frames.removeFirstOrNull()

  @Synchronized
  fun depth(): Int = frames.size

  @Synchronized
  fun discardUntilIdr() {
    if (!isWaitingForIdr) recoveries++
    dropped += frames.size
    frames.clear()
    isWaitingForIdr = true
    needsFlush = true
  }
}
