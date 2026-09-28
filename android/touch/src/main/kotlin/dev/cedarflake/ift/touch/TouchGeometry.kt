package dev.cedarflake.ift.touch

import dev.cedarflake.ift.settings.ControlSettings

class TouchGeometry(val width: Float, val height: Float, val settings: ControlSettings) {
  init {
    require(width.isFinite() && height.isFinite() && width > 0 && height > 0)
  }

  val laneTop = height * (1f - settings.laneHeight)
  val fieldTop = (laneTop - height * settings.fieldHeight).coerceAtLeast(0f)
  val fieldLeft = width * settings.fieldLeft
  val fieldRight = width * settings.fieldRight

  fun laneAt(x: Float, y: Float): Int? {
    if (!x.isFinite() || !y.isFinite() || x < 0f || x >= width || y < laneTop || y >= height) {
      return null
    }
    return (x / width * 6).toInt().coerceIn(0, 5)
  }

  fun isField(x: Float, y: Float): Boolean =
    x.isFinite() && y.isFinite() && x >= fieldLeft && x < fieldRight && y >= fieldTop && y < laneTop

  fun normalizedX(x: Float): Float = ((x - fieldLeft) / (fieldRight - fieldLeft)).coerceIn(0f, 1f)
}
