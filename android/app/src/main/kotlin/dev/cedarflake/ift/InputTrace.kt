package dev.cedarflake.ift

import android.util.Log
import android.view.MotionEvent

internal object InputTrace {
  private const val TAG = "InFalsusTouchInput"
  private var lastMoveTime = 0L
  val enabled get() = Log.isLoggable(TAG, Log.DEBUG)

  fun write(message: String) {
    if (enabled) Log.d(TAG, message)
  }

  fun motion(event: MotionEvent, source: String = "touch") {
    if (!enabled) return
    if (event.actionMasked == MotionEvent.ACTION_MOVE) {
      if (event.pointerCount < 3 || event.eventTime - lastMoveTime < 100) return
      lastMoveTime = event.eventTime
    }
    val pointers = (0 until event.pointerCount).joinToString { index ->
      "${event.getPointerId(index)}:${event.getX(index).toInt()},${event.getY(index).toInt()}"
    }
    write("$source ${MotionEvent.actionToString(event.action)} time=${event.eventTime} pointers=[$pointers] flags=${event.flags}")
  }
}
