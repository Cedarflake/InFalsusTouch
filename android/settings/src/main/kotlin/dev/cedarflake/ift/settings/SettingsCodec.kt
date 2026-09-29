package dev.cedarflake.ift.settings

object SettingsCodec {
  fun encode(value: ControlSettings): Map<String, Any> = with(value) { mapOf(
    "language" to language, "theme" to theme, "controlsMask" to controlsMask, "fieldMode" to fieldMode.name, "layoutMode" to layoutMode.name,
    "videoScale" to videoScale.name, "laneHeight" to laneHeight.toDouble(), "fieldHeight" to fieldHeight.toDouble(),
    "fieldLeft" to fieldLeft.toDouble(), "fieldRight" to fieldRight.toDouble(),
    "laneOpacity" to laneOpacity.toDouble(), "laneGapDp" to laneGapDp.toDouble(), "brightness" to brightness.toDouble(),
    "showLabels" to showLabels, "buttonHaptics" to buttonHaptics,
    "showStatistics" to showStatistics, "showFieldGuide" to showFieldGuide,
    "autoConnect" to autoConnect, "autoHideControls" to autoHideControls, "videoFps" to videoFps,
    "judgment" to with(judgment) { mapOf(
      "fieldLeft" to fieldLeft.toDouble(), "fieldRight" to fieldRight.toDouble(), "fieldY" to fieldY.toDouble(),
      "floorLeft" to floorLeft.toDouble(), "floorRight" to floorRight.toDouble(), "floorY" to floorY.toDouble(),
      "sideLeft" to sideLeft.toDouble(), "sideRight" to sideRight.toDouble(), "sideY" to sideY.toDouble(),
      "hitPadding" to hitPadding.toDouble(),
    ) },
  ) }

  fun apply(base: ControlSettings, patch: Map<*, *>): ControlSettings {
    require(patch.keys.all { it in encode(base).keys }) { "Unknown setting" }
    fun string(key: String, default: String): String = if (key in patch) requireNotNull(patch[key] as? String) else default
    fun number(key: String, default: Float): Float = if (key in patch) requireNotNull(patch[key] as? Number).toFloat() else default
    fun boolean(key: String, default: Boolean): Boolean = if (key in patch) requireNotNull(patch[key] as? Boolean) else default
    val judgment = if ("judgment" in patch) {
      val values = requireNotNull(patch["judgment"] as? Map<*, *>)
      val known = setOf("fieldLeft", "fieldRight", "fieldY", "floorLeft", "floorRight", "floorY", "sideLeft", "sideRight", "sideY", "hitPadding")
      require(values.keys.all { it in known }) { "Unknown judgment coordinate" }
      fun point(key: String, default: Float) = if (key in values) requireNotNull(values[key] as? Number).toFloat() else default
      with(base.judgment) { copy(
        fieldLeft = point("fieldLeft", fieldLeft), fieldRight = point("fieldRight", fieldRight), fieldY = point("fieldY", fieldY),
        floorLeft = point("floorLeft", floorLeft), floorRight = point("floorRight", floorRight), floorY = point("floorY", floorY),
        sideLeft = point("sideLeft", sideLeft), sideRight = point("sideRight", sideRight), sideY = point("sideY", sideY),
        hitPadding = point("hitPadding", hitPadding),
      ) }
    } else base.judgment
    return base.copy(
      language = string("language", base.language),
      theme = string("theme", base.theme),
      controlsMask = if ("controlsMask" in patch) requireNotNull(patch["controlsMask"] as? Int) else base.controlsMask,
      fieldMode = FieldMode.valueOf(string("fieldMode", base.fieldMode.name)),
      layoutMode = LayoutMode.valueOf(string("layoutMode", base.layoutMode.name)),
      videoScale = VideoScale.valueOf(string("videoScale", base.videoScale.name)),
      laneHeight = number("laneHeight", base.laneHeight), fieldHeight = number("fieldHeight", base.fieldHeight),
      fieldLeft = number("fieldLeft", base.fieldLeft), fieldRight = number("fieldRight", base.fieldRight),
      laneOpacity = number("laneOpacity", base.laneOpacity), laneGapDp = number("laneGapDp", base.laneGapDp),
      brightness = number("brightness", base.brightness), showLabels = boolean("showLabels", base.showLabels),
      buttonHaptics = boolean("buttonHaptics", base.buttonHaptics),
      showStatistics = boolean("showStatistics", base.showStatistics), showFieldGuide = boolean("showFieldGuide", base.showFieldGuide),
      autoConnect = boolean("autoConnect", base.autoConnect), autoHideControls = boolean("autoHideControls", base.autoHideControls),
      videoFps = if ("videoFps" in patch) requireNotNull(patch["videoFps"] as? Int) else base.videoFps,
      judgment = judgment,
    )
  }
}
