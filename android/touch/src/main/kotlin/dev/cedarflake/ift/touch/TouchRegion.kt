package dev.cedarflake.ift.touch

data class TouchRegion(
  val left: Float,
  val topLeft: Float,
  val right: Float,
  val topRight: Float,
  val bottom: Float,
  val judgmentLeft: Float = topLeft,
  val judgmentRight: Float = topRight,
) {
  fun contains(x: Float, y: Float): Boolean {
    if (!x.isFinite() || !y.isFinite() || x < left || x >= right || y >= bottom) return false
    val top = topLeft + (topRight - topLeft) * (x - left) / (right - left)
    return y >= top
  }
}
