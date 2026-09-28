package dev.cedarflake.ift

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.view.SurfaceView
import android.widget.FrameLayout

import dev.cedarflake.ift.settings.ControlSettings
import dev.cedarflake.ift.settings.LayoutMode
import dev.cedarflake.ift.settings.VideoScale

import kotlin.math.max
import kotlin.math.min

@SuppressLint("ViewConstructor")
class VideoViewport(context: Context) : FrameLayout(context) {
  val surface = SurfaceView(context)
  private var videoWidth = 1280
  private var videoHeight = 720
  private var settings = ControlSettings()
  private val visibleBounds = Rect()

  init {
    setBackgroundColor(Color.BLACK)
    clipChildren = true
    surface.tag = "video-surface"
    addView(surface)
  }

  fun setFormat(width: Int, height: Int) {
    videoWidth = width
    videoHeight = height
    requestLayout()
  }

  fun setSettings(value: ControlSettings) {
    settings = value
    requestLayout()
  }

  override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
    val availableWidth = right - left
    val availableHeight = if (settings.layoutMode == LayoutMode.RESERVED) {
      ((bottom - top) * (1 - settings.laneHeight)).toInt()
    } else bottom - top
    visibleBounds.set(0, 0, availableWidth, availableHeight)
    clipBounds = visibleBounds
    val scaleX = availableWidth.toFloat() / videoWidth
    val scaleY = availableHeight.toFloat() / videoHeight
    val scale = if (settings.videoScale == VideoScale.CROP) max(scaleX, scaleY) else min(scaleX, scaleY)
    val width = if (settings.videoScale == VideoScale.FILL) availableWidth else (videoWidth * scale).toInt()
    val height = if (settings.videoScale == VideoScale.FILL) availableHeight else (videoHeight * scale).toInt()
    val x = (availableWidth - width) / 2
    val y = (availableHeight - height) / 2
    surface.layout(x, y, x + width, y + height)
  }
}
