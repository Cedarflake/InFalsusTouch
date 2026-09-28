package dev.cedarflake.ift

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import dev.cedarflake.ift.settings.ControlSettings
import dev.cedarflake.ift.touch.TouchController
import dev.cedarflake.ift.touch.TouchGeometry
import dev.cedarflake.ift.touch.TouchSink

// Constructed by the Activity with a required input sink; never inflated from XML.
@SuppressLint("ViewConstructor")
class ControllerView(context: Context, private val sink: TouchSink) : View(context) {
  private var settings = ControlSettings()
  private var geometry = TouchGeometry(1f, 1f, settings)
  private val controller = TouchController(sink, geometry)
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val labels = arrayOf("1  Shift", "2  A", "3  S", "4  D", "5  F", "6  Space")
  private val density = resources.displayMetrics.density
  private val fieldLabel = context.getString(R.string.field_label)
  private val phaseLabel = context.getString(R.string.phase_label)
  private var isInputAllowed = false
  private var isVideoVisible = false

  init {
    isClickable = true
    contentDescription = context.getString(R.string.input_accessibility)
  }

  fun setInputAllowed(allowed: Boolean) {
    if (isInputAllowed && !allowed) controller.cancel()
    isInputAllowed = allowed
    if (!allowed) controller.reset()
    invalidate()
  }

  fun setSettings(value: ControlSettings) {
    settings = value
    updateGeometry()
  }

  fun setVideoVisible(visible: Boolean) {
    if (isVideoVisible == visible) return
    isVideoVisible = visible
    invalidate()
  }

  fun releaseTouches() {
    controller.cancel()
    invalidate()
  }

  override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
    updateGeometry()
  }

  private fun updateGeometry() {
    if (width <= 0 || height <= 0) return
    geometry = TouchGeometry(width.toFloat(), height.toFloat(), settings)
    controller.resize(geometry)
    invalidate()
  }

  override fun onTouchEvent(event: MotionEvent): Boolean {
    if (!isInputAllowed) return true
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
        parent.requestDisallowInterceptTouchEvent(true)
        requestUnbufferedDispatch(event)
        val index = event.actionIndex
        controller.down(event.getPointerId(index), event.getX(index), event.getY(index))
      }
      MotionEvent.ACTION_MOVE -> {
        for (index in 0 until event.pointerCount) {
          controller.move(event.getPointerId(index), event.getX(index))
        }
      }
      MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
        controller.up(event.getPointerId(event.actionIndex))
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
      }
      MotionEvent.ACTION_CANCEL -> controller.cancel()
    }
    invalidate()
    return true
  }

  override fun performClick(): Boolean {
    super.performClick()
    return true
  }

  override fun onDetachedFromWindow() {
    controller.cancel()
    super.onDetachedFromWindow()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    if (!isVideoVisible) canvas.drawColor(Color.rgb(16, 21, 28))
    paint.color = Color.rgb(148, 162, 181)
    paint.textSize = 14f * density
    paint.textAlign = Paint.Align.CENTER
    if (!isVideoVisible) canvas.drawText(phaseLabel, width / 2f, 80f * density, paint)
    paint.color = Color.rgb(97, 211, 197)
    if (!isVideoVisible && settings.showLabels) canvas.drawText(fieldLabel, width / 2f, (geometry.fieldTop + geometry.laneTop) / 2, paint)
    val laneWidth = width / 6f
    val gap = settings.laneGapDp * density / 2
    for (lane in 0..5) {
      val pressed = controller.laneCount(lane) > 0
      val brightness = settings.brightness
      paint.color = if (pressed) Color.rgb((97 * brightness).toInt(), (211 * brightness).toInt(), (197 * brightness).toInt())
        else Color.argb((255 * settings.laneOpacity).toInt(), 65, 83, 108)
      canvas.drawRect(lane * laneWidth + gap, geometry.laneTop, (lane + 1) * laneWidth - gap, height.toFloat(), paint)
      if (settings.showLabels) {
        paint.color = if (pressed) Color.BLACK else Color.WHITE
        paint.textSize = 16f * density
        canvas.drawText(labels[lane], (lane + 0.5f) * laneWidth, geometry.laneTop + (height - geometry.laneTop) / 2, paint)
      }
    }
  }
}
