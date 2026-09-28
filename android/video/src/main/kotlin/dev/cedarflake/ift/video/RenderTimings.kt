package dev.cedarflake.ift.video

internal class RenderTimings(private val capacity: Int = 64) {
  private val frames = LinkedHashMap<Long, FrameTiming>()

  init { require(capacity > 0) }

  @Synchronized
  fun add(presentationUs: Long, timing: FrameTiming) {
    frames[presentationUs] = timing
    if (frames.size > capacity) frames.remove(frames.keys.first())
  }

  @Synchronized
  fun take(presentationUs: Long): FrameTiming? = frames.remove(presentationUs)

  @Synchronized
  fun clear() = frames.clear()
}
