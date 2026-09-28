package dev.cedarflake.ift

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
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
  private val controller = TouchController(object : TouchSink by sink {
    override fun laneDown(lane: Int) {
      sink.laneDown(lane)
      if (settings.buttonHaptics) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
  }, geometry)
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val lanePaths = Array(6) { Path() }
  private val fieldPath = Path()
  private var videoPlacement: VideoPlacement? = null
  private val labels = arrayOf("Shift", "A", "S", "D", "F", "Space")
  private val numbers = arrayOf("1", "2", "3", "4", "5", "6")
  private val labelMetrics = Paint.FontMetrics()
  private var numberSize = 0f
  private var keySize = 0f
  private var numberAscent = 0f
  private var keyAscent = 0f
  private var numberHeight = 0f
  private var labelHeight = 0f
  private val density = resources.displayMetrics.density
  private var fieldLabel = context.getString(R.string.field_label)
  private var isInputAllowed = false
  private var isVideoVisible = false
  private var isFieldBusy = false

  fun setBindings(value: List<String>) {
    require(value.size == 6)
    for (index in labels.indices) labels[index] = value[index]
    invalidate()
  }

  fun setFieldBusy(value: Boolean) { isFieldBusy = value; invalidate() }

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
    val language = if (value.language == "system") resources.configuration.locales[0].language else value.language
    fieldLabel = if (language == "zh") "FIELD · 水平滑动" else "FIELD · slide horizontally"
    contentDescription = if (language == "zh") "六轨按键与水平 Field 触控区" else context.getString(R.string.input_accessibility)
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
    fieldPath.apply {
      rewind()
      moveTo(geometry.fieldLeft, geometry.fieldTop)
      lineTo(geometry.fieldRight, geometry.fieldTop)
      for (point in geometry.fieldBoundary.asReversed()) lineTo(point.x, point.y)
      close()
    }
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
    val availableHeight = geometry.laneRegions.minOf { it.bottom - maxOf(it.judgmentLeft, it.judgmentRight) } - 6f * density
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textSize = 24f * density
    paint.getFontMetrics(labelMetrics)
    val largeHeight = labelMetrics.descent - labelMetrics.ascent
    paint.typeface = Typeface.DEFAULT
    paint.textSize = 12f * density
    paint.getFontMetrics(labelMetrics)
    val scale = (availableHeight / (largeHeight + labelMetrics.descent - labelMetrics.ascent + 2f * density)).coerceIn(0.4f, 1f)
    numberSize = 24f * density * scale
    keySize = 12f * density * scale
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textSize = numberSize
    paint.getFontMetrics(labelMetrics)
    numberAscent = labelMetrics.ascent
    numberHeight = labelMetrics.descent - labelMetrics.ascent
    paint.typeface = Typeface.DEFAULT
    paint.textSize = keySize
    paint.getFontMetrics(labelMetrics)
    keyAscent = labelMetrics.ascent
    labelHeight = numberHeight + 2f * density + labelMetrics.descent - labelMetrics.ascent
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
        val index = event.actionIndex
        controller.move(event.getPointerId(index), event.getX(index))
        controller.up(event.getPointerId(index))
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
    paint.color = Color.rgb(97, 211, 197)
    if (!isVideoVisible && settings.showLabels && settings.controlsMask and 64 != 0) canvas.drawText(fieldLabel, width / 2f, (geometry.fieldTop + geometry.fieldBottom) / 2, paint)
    canvas.save()
    canvas.clipRect(geometry.clipLeft, geometry.clipTop, geometry.clipRight, geometry.clipBottom)
    if (settings.showFieldGuide && settings.controlsMask and 64 != 0) {
      paint.style = Paint.Style.STROKE
      paint.strokeWidth = density
      canvas.drawPath(fieldPath, paint)
      paint.style = Paint.Style.FILL
    }
    if (controller.fieldPointerId >= 0) {
      paint.color = if (isFieldBusy) Color.rgb(247, 197, 115) else Color.rgb(97, 211, 197)
      paint.strokeWidth = 2f * density
      canvas.drawLine(geometry.fieldLeft, geometry.fieldLineY, geometry.fieldRight, geometry.fieldLineY, paint)
      val fieldX = geometry.fieldLeft + controller.fieldNormalizedX * (geometry.fieldRight - geometry.fieldLeft)
      canvas.drawCircle(fieldX, geometry.fieldLineY, 7f * density, paint)
    }
    for (lane in 0..5) {
      if (settings.controlsMask and (1 shl lane) == 0) continue
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
        val center = (region.left + region.right) / 2
        val top = maxOf(region.judgmentLeft, region.judgmentRight)
        val labelTop = top + (region.bottom - top - labelHeight) / 2
        paint.color = if (pressed) Color.WHITE else Color.rgb(224, 233, 231)
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = numberSize
        canvas.drawText(numbers[lane], center, labelTop - numberAscent, paint)
        paint.color = if (pressed) Color.rgb(187, 248, 237) else Color.rgb(169, 187, 190)
        paint.typeface = Typeface.DEFAULT
        paint.textSize = keySize
        val availableWidth = region.right - region.left - 8f * density
        val labelWidth = paint.measureText(labels[lane])
        if (labelWidth > availableWidth) paint.textSize *= availableWidth / labelWidth
        canvas.drawText(labels[lane], center, labelTop + numberHeight + 2f * density - keyAscent, paint)
      }
    }
    canvas.restore()
  }
}
