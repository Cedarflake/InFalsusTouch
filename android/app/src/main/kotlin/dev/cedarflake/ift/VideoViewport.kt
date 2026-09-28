package dev.cedarflake.ift

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.view.SurfaceView
import android.widget.FrameLayout

import dev.cedarflake.ift.settings.ControlSettings
import dev.cedarflake.ift.settings.placeVideo

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
    val availableHeight = bottom - top
    if (availableWidth <= 0 || availableHeight <= 0) return
    val placement = placeVideo(availableWidth, availableHeight, videoWidth, videoHeight, settings)
    visibleBounds.set(0, 0, availableWidth, placement.clipHeight)
    clipBounds = visibleBounds
    surface.layout(placement.left, placement.top, placement.left + placement.width, placement.top + placement.height)
  }
}
