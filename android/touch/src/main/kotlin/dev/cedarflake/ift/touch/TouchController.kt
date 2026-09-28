package dev.cedarflake.ift.touch

import dev.cedarflake.ift.settings.FieldMode

interface TouchSink {
  fun laneDown(lane: Int)
  fun laneUp(lane: Int)
  fun fieldAbsolute(x: Float)
  fun fieldRelative(deltaX: Float)
  fun releaseAll()
}

class TouchController(private val sink: TouchSink, private var geometry: TouchGeometry) {
  private val owners = IntArray(32)
  private val laneCounts = IntArray(6)
  private var previousFieldX = 0f
  var fieldPointerId = -1
    private set
  val fieldNormalizedX: Float get() = previousFieldX

  fun laneCount(lane: Int): Int = laneCounts[lane]

  fun resize(value: TouchGeometry) {
    cancel()
    geometry = value
  }

  fun down(pointerId: Int, x: Float, y: Float) {
    if (pointerId !in owners.indices || owners[pointerId] != NONE) return
    val lane = geometry.laneAt(x, y)
    if (lane != null) {
      owners[pointerId] = lane + 1
      if (laneCounts[lane]++ == 0) sink.laneDown(lane + 1)
      return
    }
    owners[pointerId] = IGNORED
    if (fieldPointerId == -1 && geometry.isField(x, y)) {
      owners[pointerId] = FIELD
      fieldPointerId = pointerId
      previousFieldX = geometry.normalizedX(x)
      if (geometry.settings.fieldMode == FieldMode.ABSOLUTE) sink.fieldAbsolute(previousFieldX)
    }
  }

  fun move(pointerId: Int, x: Float) {
    if (pointerId != fieldPointerId || !x.isFinite()) return
    val currentX = geometry.normalizedX(x)
    if (currentX == previousFieldX) return
    if (geometry.settings.fieldMode == FieldMode.ABSOLUTE) {
      sink.fieldAbsolute(currentX)
    } else {
      sink.fieldRelative(currentX - previousFieldX)
    }
    previousFieldX = currentX
  }

  fun up(pointerId: Int) {
    if (pointerId !in owners.indices) return
    val owner = owners[pointerId]
    owners[pointerId] = NONE
    if (owner in 1..6 && --laneCounts[owner - 1] == 0) sink.laneUp(owner)
    if (pointerId == fieldPointerId) fieldPointerId = -1
  }

  fun cancel() {
    reset()
    sink.releaseAll()
  }

  fun reset() {
    owners.fill(NONE)
    laneCounts.fill(0)
    fieldPointerId = -1
    previousFieldX = 0f
  }

  private companion object {
    const val NONE = 0
    const val FIELD = 7
    const val IGNORED = 8
  }
}
