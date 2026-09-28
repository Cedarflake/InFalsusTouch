package dev.cedarflake.ift.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VideoLayoutTest {
  @Test fun fitAndCropPreserveAspectAndCenterOnWidePhone() {
    val fit = placeVideo(2400, 1080, 1280, 720, ControlSettings())
    assertEquals(VideoPlacement(240, 0, 1920, 1080, 1080), fit)
    val crop = placeVideo(2400, 1080, 1280, 720, ControlSettings(videoScale = VideoScale.CROP))
    assertEquals(VideoPlacement(0, -135, 2400, 1350, 1080), crop)
    assertEquals(VideoPlacement(0, 0, 2400, 1080, 1080),
      placeVideo(2400, 1080, 1280, 720, ControlSettings(videoScale = VideoScale.FILL)))
  }

  @Test fun reservedClipsVideoAboveLanesForAllScaleModes() {
    for (scale in VideoScale.entries) {
      val result = placeVideo(2400, 1000, 1280, 720,
        ControlSettings(layoutMode = LayoutMode.RESERVED, laneHeight = 0.3f, videoScale = scale))
      assertEquals(700, result.clipHeight)
      assertEquals((700 - result.height) / 2, result.top)
      if (scale == VideoScale.FIT) assertTrue(result.top >= 0 && result.height <= 700)
      if (scale == VideoScale.CROP) assertTrue(result.top < 0)
    }
  }

  @Test fun rejectsInvalidSettingsAndDimensions() {
    assertFailsWith<IllegalArgumentException> { ControlSettings(fieldLeft = 0.8f, fieldRight = 0.2f) }
    assertFailsWith<IllegalArgumentException> { ControlSettings(laneOpacity = Float.NaN) }
    assertFailsWith<IllegalArgumentException> { placeVideo(0, 100, 1280, 720, ControlSettings()) }
  }
}
