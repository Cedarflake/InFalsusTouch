package dev.cedarflake.ift.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SettingsCodecTest {
  @Test fun partialLanguageChangePreservesCalibratedCoordinates() {
    val settings = ControlSettings(theme = "light", buttonHaptics = true, videoFps = 90,
      fieldLeft = 0.12345f, judgment = JudgmentLayout(fieldLeft = 0.08236f))
    assertEquals(settings.copy(language = "zh"), SettingsCodec.apply(settings, mapOf("language" to "zh")))
    assertEquals(settings, SettingsCodec.apply(ControlSettings(), SettingsCodec.encode(settings)))
  }

  @Test fun rejectsInvalidPlatformPayloadsBeforeTheyCanChangeInputGeometry() {
    for (patch in listOf(mapOf("language" to "unknown"), mapOf("theme" to "unknown"), mapOf("laneHeight" to Double.NaN),
      mapOf("showLabels" to "true"), mapOf("buttonHaptics" to "true"),
      mapOf("videoFps" to 23), mapOf("videoFps" to 121), mapOf("videoFps" to 60.5),
      mapOf("unknown" to 1), mapOf("judgment" to mapOf("floorLeft" to 0.95)))) {
      assertFailsWith<IllegalArgumentException> { SettingsCodec.apply(ControlSettings(), patch) }
    }
  }
}
