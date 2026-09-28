package dev.cedarflake.ift

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import dev.cedarflake.ift.settings.ControlSettings
import dev.cedarflake.ift.settings.VideoPlacement
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
  private val lanePaths = Array(6) { Path() }
  private var videoPlacement: VideoPlacement? = null
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
    updateGeometry()
  }

  fun setVideoPlacement(value: VideoPlacement) {
    if (videoPlacement == value) return
    videoPlacement = value
    updateGeometry()
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
    geometry = TouchGeometry(width.toFloat(), height.toFloat(), settings, videoPlacement.takeIf { isVideoVisible })
    controller.resize(geometry)
    val gap = settings.laneGapDp * density / 2
    geometry.laneRegions.forEachIndexed { index, region ->
      lanePaths[index].apply {
        rewind()
        moveTo(region.left + gap, region.topLeft)
        lineTo(region.right - gap, region.topRight)
        lineTo(region.right - gap, region.bottom)
        lineTo(region.left + gap, region.bottom)
        close()
      }
    }
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
    canvas.save()
    canvas.clipRect(geometry.clipLeft, geometry.clipTop, geometry.clipRight, geometry.clipBottom)
    if (settings.showFieldGuide) {
      paint.style = Paint.Style.STROKE
      paint.strokeWidth = density
      canvas.drawRect(geometry.fieldLeft, geometry.fieldTop, geometry.fieldRight, geometry.fieldBottom, paint)
      paint.style = Paint.Style.FILL
    }
    if (controller.fieldPointerId >= 0) {
      paint.color = Color.rgb(97, 211, 197)
      paint.strokeWidth = 2f * density
      canvas.drawLine(geometry.fieldLeft, geometry.fieldLineY, geometry.fieldRight, geometry.fieldLineY, paint)
      val fieldX = geometry.fieldLeft + controller.fieldNormalizedX * (geometry.fieldRight - geometry.fieldLeft)
      canvas.drawCircle(fieldX, geometry.fieldLineY, 7f * density, paint)
    }
    for (lane in 0..5) {
      val region = geometry.laneRegions[lane]
      val pressed = controller.laneCount(lane) > 0
      val brightness = settings.brightness
      paint.color = if (pressed) Color.argb(115, (97 * brightness).toInt(), (211 * brightness).toInt(), (197 * brightness).toInt())
        else Color.argb((255 * settings.laneOpacity).toInt(), (65 * brightness).toInt(), (83 * brightness).toInt(), (108 * brightness).toInt())
      canvas.drawPath(lanePaths[lane], paint)
      if (pressed) {
        paint.color = Color.rgb((97 * brightness).toInt(), (211 * brightness).toInt(), (197 * brightness).toInt())
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        canvas.drawPath(lanePaths[lane], paint)
        paint.strokeWidth = 4f * density
        canvas.drawLine(region.left, region.judgmentLeft, region.right, region.judgmentRight, paint)
        paint.style = Paint.Style.FILL
      }
      if (settings.showLabels) {
        paint.color = Color.WHITE
        paint.textSize = 16f * density
        val top = maxOf(region.judgmentLeft, region.judgmentRight)
        canvas.drawText(labels[lane], (region.left + region.right) / 2, top + (region.bottom - top) / 2, paint)
      }
    }
    canvas.restore()
  }
}
