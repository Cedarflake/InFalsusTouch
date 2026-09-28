package dev.cedarflake.ift

import android.content.Context
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

class ControllerRoot(context: Context) : FrameLayout(context) {
  lateinit var gameplay: View
  lateinit var interfaceView: View
  var isConfiguring = false
  var interfaceRegions: List<RectF> = emptyList()
  private var gestureTarget: View? = null

  override fun dispatchTouchEvent(event: MotionEvent): Boolean {
    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
      val x = event.x - interfaceView.left
      val y = event.y - interfaceView.top
      gestureTarget = if (isConfiguring || interfaceRegions.any { it.contains(x, y) }) interfaceView else gameplay
    }
    val target = gestureTarget ?: return false
    // Keep every pointer of a gameplay gesture native, even when a finger crosses the menu.
    val local = MotionEvent.obtain(event)
    try {
      local.offsetLocation(-target.left.toFloat(), -target.top.toFloat())
      target.dispatchTouchEvent(local)
    } finally {
      local.recycle()
      if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) gestureTarget = null
    }
    return true
  }
}
