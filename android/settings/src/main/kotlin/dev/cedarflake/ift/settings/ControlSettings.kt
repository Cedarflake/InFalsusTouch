package dev.cedarflake.ift.settings

enum class FieldMode { ABSOLUTE, RELATIVE }
enum class LayoutMode { OVERLAY, RESERVED, ALIGNED }
enum class VideoScale { FIT, FILL, CROP }

data class ControlSettings(
  val language: String = "system",
  val theme: String = "dark",
  val controlsMask: Int = 127,
  val fieldMode: FieldMode = FieldMode.ABSOLUTE,
  val layoutMode: LayoutMode = LayoutMode.ALIGNED,
  val videoScale: VideoScale = VideoScale.FIT,
  val laneHeight: Float = 0.4f,
  val fieldHeight: Float = 0.65f,
  val fieldLeft: Float = 0f,
  val fieldRight: Float = 1f,
  val laneOpacity: Float = 0.45f,
  val laneGapDp: Float = 2f,
  val brightness: Float = 1f,
  val showLabels: Boolean = true,
  val showStatistics: Boolean = false,
  val showFieldGuide: Boolean = false,
  val autoConnect: Boolean = false,
  val autoHideControls: Boolean = true,
  val highRefreshDisplay: Boolean = true,
  val judgment: JudgmentLayout = JudgmentLayout(),
) {
  init {
    require(language in setOf("system", "en", "zh"))
    require(theme in setOf("system", "light", "dark"))
    require(controlsMask in 0..127)
    require(laneHeight in 0.1f..0.5f)
    require(fieldHeight in 0.1f..1f)
    require(fieldLeft in 0f..1f && fieldRight in 0f..1f && fieldLeft < fieldRight)
    require(laneOpacity in 0f..1f && brightness in 0.1f..1f && laneGapDp in 0f..20f)
  }
}
