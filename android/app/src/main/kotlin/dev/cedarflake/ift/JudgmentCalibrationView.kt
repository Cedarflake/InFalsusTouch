package dev.cedarflake.ift

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

import dev.cedarflake.ift.settings.JudgmentLayout
import dev.cedarflake.ift.settings.VideoPlacement

@SuppressLint("ViewConstructor")
class JudgmentCalibrationView(
  context: Context,
  private val placement: () -> VideoPlacement?,
  private val current: JudgmentLayout,
  private val completed: (JudgmentLayout) -> Unit,
  private val cancelled: () -> Unit,
) : FrameLayout(context) {
  private val points = ArrayList<PointF>(6)
  private val prompts = resources.getStringArray(R.array.judgment_calibration_steps)
  private val instruction = TextView(context).apply {
    textSize = 17f
    gravity = Gravity.CENTER
    setTextColor(Color.WHITE)
    setPadding(12, 8, 12, 8)
  }
  private val save = Button(context).apply { setText(R.string.save_settings); isEnabled = false; tag = "judgment-save" }
  private var result: JudgmentLayout? = null
  private val canvasView = object : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init { isClickable = true }

    override fun onDraw(canvas: Canvas) {
      canvas.drawColor(0x44000000)
      val video = placement() ?: return
      paint.color = Color.rgb(97, 211, 197)
      paint.strokeWidth = 3 * resources.displayMetrics.density
      for ((index, point) in points.withIndex()) {
        val x = video.left + point.x * video.width
        val y = video.top + point.y * video.height
        canvas.drawCircle(x, y, 5 * resources.displayMetrics.density, paint)
        if (index % 2 == 1) {
          val previous = points[index - 1]
          canvas.drawLine(video.left + previous.x * video.width, video.top + previous.y * video.height, x, y, paint)
        }
      }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
      if (event.actionMasked == MotionEvent.ACTION_UP && points.size < 6) {
        val video = placement()
        if (video != null && event.x >= maxOf(0, video.left) && event.x < minOf(width, video.left + video.width) &&
          event.y >= maxOf(0, video.top) && event.y < minOf(video.clipHeight, video.top + video.height)) {
          points += PointF((event.x - video.left) / video.width, (event.y - video.top) / video.height)
          updateResult()
        }
        performClick()
      }
      return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
  }

  init {
    tag = "judgment-calibration"
    addView(canvasView, LayoutParams(-1, -1))
    val panel = LinearLayout(context).apply {
      orientation = LinearLayout.VERTICAL
      setBackgroundColor(0xdd10151c.toInt())
    }
    panel.addView(instruction)
    val actions = LinearLayout(context).apply { gravity = Gravity.CENTER }
    actions.addView(Button(context).apply {
      setText(android.R.string.cancel)
      tag = "calibration-cancel"
      setOnClickListener { cancelled() }
    })
    actions.addView(Button(context).apply {
      setText(R.string.calibration_undo)
      setOnClickListener {
        if (points.isNotEmpty()) points.removeAt(points.lastIndex)
        updateResult()
      }
    })
    actions.addView(save)
    save.setOnClickListener { result?.let(completed) }
    panel.addView(actions)
    addView(panel, LayoutParams(-1, -2, Gravity.TOP))
    updateResult()
  }

  private fun updateResult() {
    result = null
    if (points.size == 6) {
      result = try {
        current.copy(fieldLeft = points[0].x, fieldRight = points[1].x, fieldY = (points[0].y + points[1].y) / 2,
          floorLeft = points[2].x, floorRight = points[3].x, floorY = (points[2].y + points[3].y) / 2,
          sideLeft = points[4].x, sideRight = points[5].x, sideY = (points[4].y + points[5].y) / 2)
      } catch (_: IllegalArgumentException) { null }
      instruction.setText(if (result != null) R.string.judgment_calibration_done else R.string.judgment_calibration_invalid)
    } else instruction.text = prompts[points.size]
    save.isEnabled = result != null
    canvasView.invalidate()
  }
}
