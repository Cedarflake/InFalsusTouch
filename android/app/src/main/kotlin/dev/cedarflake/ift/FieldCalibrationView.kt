package dev.cedarflake.ift

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

import dev.cedarflake.ift.settings.ControlSettings

@SuppressLint("ViewConstructor")
class FieldCalibrationView(
  context: Context,
  private val settings: ControlSettings,
  private val completed: (Float, Float) -> Unit,
  private val cancelled: () -> Unit,
) : FrameLayout(context) {
  private var leftEdge = settings.fieldLeft
  private var rightEdge = settings.fieldRight
  private var step = 0
  private val instruction = TextView(context).apply {
    textSize = 18f
    gravity = Gravity.CENTER
    setTextColor(Color.WHITE)
    setBackgroundColor(0xcc10151c.toInt())
    setPadding(12, 12, 12, 12)
    setText(R.string.calibration_left)
  }
  private val save = Button(context).apply { setText(R.string.save_settings); isEnabled = false; tag = "calibration-save" }

  init {
    tag = "field-calibration"
    addView(object : View(context) {
      private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
      init { isClickable = true; contentDescription = context.getString(R.string.calibrate_touch) }

      override fun onDraw(canvas: Canvas) {
        canvas.drawColor(0x88000000.toInt())
        val bottom = height * (1 - settings.laneHeight)
        val top = (bottom - height * settings.fieldHeight).coerceAtLeast(0f)
        paint.color = 0x5561d3c5
        paint.style = Paint.Style.FILL
        canvas.drawRect(leftEdge * width, top, rightEdge * width, bottom, paint)
        paint.color = Color.rgb(97, 211, 197)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3 * resources.displayMetrics.density
        canvas.drawRect(leftEdge * width, top, rightEdge * width, bottom, paint)
      }

      override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
          val bottom = height * (1 - settings.laneHeight)
          val top = (bottom - height * settings.fieldHeight).coerceAtLeast(0f)
          if (event.y in top..bottom && width > 0) {
            val x = (event.x / width).coerceIn(0f, 1f)
            if (step == 0) {
              leftEdge = x.coerceAtMost(0.95f)
              rightEdge = 1f
              step = 1
              instruction.setText(R.string.calibration_right)
            } else if (x >= leftEdge + 0.05f) {
              rightEdge = x
              step = 2
              instruction.setText(R.string.calibration_done)
              save.isEnabled = true
            } else instruction.setText(R.string.calibration_range_error)
            invalidate()
          }
          performClick()
        }
        return true
      }

      override fun performClick(): Boolean { super.performClick(); return true }
    }, LayoutParams(-1, -1))
    addView(instruction, LayoutParams(-1, -2, Gravity.TOP))
    val actions = LinearLayout(context).apply { gravity = Gravity.CENTER }
    actions.addView(Button(context).apply {
      setText(android.R.string.cancel)
      tag = "calibration-cancel"
      setOnClickListener { cancelled() }
    })
    actions.addView(Button(context).apply {
      setText(R.string.calibration_restart)
      setOnClickListener {
        step = 0
        leftEdge = settings.fieldLeft
        rightEdge = settings.fieldRight
        save.isEnabled = false
        instruction.setText(R.string.calibration_left)
        getChildAt(0).invalidate()
      }
    })
    actions.addView(save)
    save.setOnClickListener { if (step == 2) completed(leftEdge, rightEdge) }
    addView(actions, LayoutParams(-1, -2, Gravity.BOTTOM))
  }
}
