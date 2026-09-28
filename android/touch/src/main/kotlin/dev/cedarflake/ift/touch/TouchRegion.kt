package dev.cedarflake.ift.touch

data class TouchPoint(val x: Float, val y: Float)

data class TouchRegion(
  val left: Float,
  val topLeft: Float,
  val right: Float,
  val topRight: Float,
  val bottom: Float,
  val judgmentLeft: Float = topLeft,
  val judgmentRight: Float = topRight,
) {
  fun topAt(x: Float): Float = topLeft + (topRight - topLeft) * (x - left) / (right - left)

  fun contains(x: Float, y: Float): Boolean {
    if (!x.isFinite() || !y.isFinite() || x < left || x >= right || y >= bottom) return false
    return y >= topAt(x)
  }
}
