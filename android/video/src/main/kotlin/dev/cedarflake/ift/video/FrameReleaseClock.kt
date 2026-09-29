package dev.cedarflake.ift.video

internal class FrameReleaseClock(fps: Int) {
  private val interval = 1_000_000_000L / fps
  private val lead = if (fps > 60) minOf(interval * 2, 16_666_666L) else 0L
  private var previous = 0L

  init { require(fps in 24..120) }

  fun next(now: Long): Long {
    val earliest = now + lead
    val latest = earliest + interval
    val target = maxOf(earliest, previous + interval).coerceAtMost(latest)
    previous = target
    return target
  }

  fun reset() { previous = 0L }
}
