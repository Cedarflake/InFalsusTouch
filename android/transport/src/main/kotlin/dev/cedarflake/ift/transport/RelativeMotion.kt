package dev.cedarflake.ift.transport

internal inline fun forEachRelativeStep(deltaPixels: Float, emit: (Float) -> Unit) {
  require(deltaPixels.isFinite() && deltaPixels in -32768f..32768f)
  // The wire value is measured in 1280-unit blocks; split large swipes without capping them.
  var remaining = deltaPixels
  while (remaining != 0f) {
    val step = remaining.coerceIn(-1280f, 1280f)
    emit(step / 1280f)
    remaining -= step
  }
}
