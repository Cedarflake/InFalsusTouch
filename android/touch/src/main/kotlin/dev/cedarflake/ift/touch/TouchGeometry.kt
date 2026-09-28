package dev.cedarflake.ift.touch

import dev.cedarflake.ift.settings.ControlSettings
import dev.cedarflake.ift.settings.LayoutMode
import dev.cedarflake.ift.settings.VideoPlacement

class TouchGeometry(val width: Float, val height: Float, val settings: ControlSettings, video: VideoPlacement? = null) {
  init {
    require(width.isFinite() && height.isFinite() && width > 0 && height > 0)
  }

  private val picture = video?.takeIf { settings.layoutMode == LayoutMode.ALIGNED }
  val isAligned = picture != null
  val clipLeft = maxOf(0f, picture?.left?.toFloat() ?: 0f)
  val clipTop = maxOf(0f, picture?.top?.toFloat() ?: 0f)
  val clipRight = minOf(width, picture?.let { (it.left + it.width).toFloat() } ?: width)
  val clipBottom = minOf(height, picture?.clipHeight?.toFloat() ?: height,
    picture?.let { (it.top + it.height).toFloat() } ?: height)
  private fun videoX(x: Float) = requireNotNull(picture).let { it.left + it.width * x }
  private fun videoY(y: Float) = requireNotNull(picture).let { it.top + it.height * y }

  private val requestedLaneTop = if (isAligned) videoY(1f - settings.laneHeight) else height * (1f - settings.laneHeight)
  val laneTop = if (isAligned) minOf(requestedLaneTop, videoY(settings.judgment.floorY - settings.judgment.hitPadding)) else requestedLaneTop
  val fieldLineY = if (isAligned) videoY(settings.judgment.fieldY) else laneTop
  val fieldBottom = if (isAligned) minOf(laneTop, videoY(settings.judgment.sideY - settings.judgment.hitPadding),
    videoY(settings.judgment.fieldY + settings.judgment.hitPadding)) else laneTop
  val fieldTop = if (isAligned) maxOf(clipTop, fieldBottom - requireNotNull(picture).height * settings.fieldHeight)
    else (laneTop - height * settings.fieldHeight).coerceAtLeast(0f)
  val fieldLeft = if (isAligned) videoX(settings.judgment.fieldLeft) else width * settings.fieldLeft
  val fieldRight = if (isAligned) videoX(settings.judgment.fieldRight) else width * settings.fieldRight
  val laneRegions: List<TouchRegion> = if (isAligned) {
    val layout = settings.judgment
    val left = videoX(layout.floorLeft)
    val right = videoX(layout.floorRight)
    val sideTop = minOf(requestedLaneTop, videoY(layout.sideY - layout.hitPadding))
    val floorLine = videoY(layout.floorY)
    val sideLine = videoY(layout.sideY)
    val bottom = videoY(1f)
    listOf(TouchRegion(videoX(layout.sideLeft), sideTop, left, laneTop, bottom, sideLine, floorLine)) +
      List(4) { lane -> TouchRegion(left + (right - left) * lane / 4, laneTop,
        left + (right - left) * (lane + 1) / 4, laneTop, bottom, floorLine, floorLine) } +
      TouchRegion(right, laneTop, videoX(layout.sideRight), sideTop, bottom, floorLine, sideLine)
  } else List(6) { lane -> TouchRegion(width * lane / 6, laneTop, width * (lane + 1) / 6, laneTop, height) }

  private fun isVisible(x: Float, y: Float): Boolean =
    x >= clipLeft && x < clipRight && y >= clipTop && y < clipBottom

  fun laneAt(x: Float, y: Float): Int? {
    if (!isVisible(x, y)) return null
    val lane = laneRegions.indexOfFirst { it.contains(x, y) }
    return lane.takeIf { it >= 0 }
  }

  fun isField(x: Float, y: Float): Boolean =
    isVisible(x, y) && x.isFinite() && y.isFinite() && x >= fieldLeft && x < fieldRight && y >= fieldTop && y < fieldBottom

  fun normalizedX(x: Float): Float = ((x - fieldLeft) / (fieldRight - fieldLeft)).coerceIn(0f, 1f)
}
