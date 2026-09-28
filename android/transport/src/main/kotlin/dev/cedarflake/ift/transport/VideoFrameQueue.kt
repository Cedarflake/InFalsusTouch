package dev.cedarflake.ift.transport

data class QueuedVideoFrame(val packet: VideoPacket, val needsFlush: Boolean)

class VideoFrameQueue(private val capacity: Int = 2) {
  private val frames = ArrayDeque<QueuedVideoFrame>()
  private var isWaitingForIdr = true
  private var needsFlush = false
  @Volatile var dropped: Long = 0
    private set

  init { require(capacity in 1..4) }

  @Synchronized
  fun offer(packet: VideoPacket) {
    require(packet.header.type == VideoPacketType.FRAME)
    if (frames.size >= capacity) discardUntilIdr()
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
    dropped += frames.size
    frames.clear()
    isWaitingForIdr = true
    needsFlush = true
  }
}
