package dev.cedarflake.ift.video

import kotlin.math.ceil

data class LatencyDistribution(val meanMs: Double, val p95Ms: Double)

data class VideoLatencySnapshot(
  val samples: Int,
  val receiveToSubmit: LatencyDistribution,
  val decode: LatencyDistribution,
  val decodeToPresent: LatencyDistribution,
  val receiveToPresent: LatencyDistribution,
)

internal class VideoLatencyWindow(private val capacity: Int = 240) {
  private val stages = Array(4) { DoubleArray(capacity) }
  private var next = 0
  private var count = 0

  init { require(capacity > 0) }

  fun record(timing: FrameTiming) {
    stages[0][next] = (timing.submitTimestamp - timing.receiveTimestamp) / 1e6
    stages[1][next] = (timing.decodeTimestamp - timing.submitTimestamp) / 1e6
    stages[2][next] = (timing.presentTimestamp - timing.decodeTimestamp) / 1e6
    stages[3][next] = (timing.presentTimestamp - timing.receiveTimestamp) / 1e6
    next = (next + 1) % capacity
    count = minOf(count + 1, capacity)
  }

  fun snapshot(): VideoLatencySnapshot? {
    if (count == 0) return null
    fun distribution(stage: DoubleArray): LatencyDistribution {
      val values = stage.copyOf(count).apply { sort() }
      return LatencyDistribution(values.average(), values[ceil(count * 0.95).toInt() - 1])
    }
    return VideoLatencySnapshot(count, distribution(stages[0]), distribution(stages[1]),
      distribution(stages[2]), distribution(stages[3]))
  }
}
