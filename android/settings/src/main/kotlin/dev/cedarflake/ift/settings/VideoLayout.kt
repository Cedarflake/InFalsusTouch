package dev.cedarflake.ift.settings

import kotlin.math.max
import kotlin.math.min

data class VideoPlacement(val left: Int, val top: Int, val width: Int, val height: Int, val clipHeight: Int)

fun placeVideo(width: Int, height: Int, videoWidth: Int, videoHeight: Int, settings: ControlSettings): VideoPlacement {
  require(width > 0 && height > 0 && videoWidth > 0 && videoHeight > 0)
  val availableHeight = if (settings.layoutMode == LayoutMode.RESERVED) (height * (1 - settings.laneHeight)).toInt().coerceAtLeast(1) else height
  val scaleX = width.toDouble() / videoWidth
  val scaleY = availableHeight.toDouble() / videoHeight
  val scale = if (settings.videoScale == VideoScale.CROP) max(scaleX, scaleY) else min(scaleX, scaleY)
  val targetWidth = if (settings.videoScale == VideoScale.FILL) width else (videoWidth * scale).toInt().coerceAtLeast(1)
  val targetHeight = if (settings.videoScale == VideoScale.FILL) availableHeight else (videoHeight * scale).toInt().coerceAtLeast(1)
  return VideoPlacement((width - targetWidth) / 2, (availableHeight - targetHeight) / 2, targetWidth, targetHeight, availableHeight)
}
