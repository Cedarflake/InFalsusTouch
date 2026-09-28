package dev.cedarflake.ift.touch

import dev.cedarflake.ift.settings.ControlSettings
import dev.cedarflake.ift.settings.JudgmentLayout
import dev.cedarflake.ift.settings.VideoScale
import dev.cedarflake.ift.settings.placeVideo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlignedTouchTest {
  @Test fun judgmentRegionsFollowFitStretchAndCropOnDifferentScreens() {
    for ((width, height) in listOf(2400 to 1080, 1920 to 1080, 1600 to 1200)) {
      for (scale in VideoScale.entries) {
        val settings = ControlSettings(videoScale = scale)
        val video = placeVideo(width, height, 1280, 720, settings)
        val geometry = TouchGeometry(width.toFloat(), height.toFloat(), settings, video)
        val centers = listOf(0.135f, 0.2825f, 0.4275f, 0.5725f, 0.7175f, 0.865f)
        for ((lane, center) in centers.withIndex()) {
          val x = video.left + video.width * center
          val y = video.top + video.height * if (lane == 0 || lane == 5) 0.82f else 0.92f
          if (y >= 0 && y < height && x >= 0 && x < width) assertEquals(lane, geometry.laneAt(x, y))
        }
        val fieldX = video.left + video.width * 0.5f
        val fieldY = video.top + video.height * 0.4f
        assertTrue(geometry.isField(fieldX, fieldY))
        assertEquals(0.5f, geometry.normalizedX(fieldX), 0.00001f)
        assertNull(geometry.laneAt(fieldX, fieldY))
      }
    }
  }

  @Test fun letterboxAndInvisibleCroppedPixelsNeverBecomeInput() {
    val settings = ControlSettings()
    val fit = TouchGeometry(2400f, 1080f, settings, placeVideo(2400, 1080, 1280, 720, settings))
    for (x in listOf(10f, 239f, 2160f, 2390f)) {
      assertNull(fit.laneAt(x, 1000f))
      assertFalse(fit.isField(x, 500f))
    }
    val cropSettings = settings.copy(videoScale = VideoScale.CROP)
    val crop = TouchGeometry(2400f, 1080f, cropSettings, placeVideo(2400, 1080, 1280, 720, cropSettings))
    assertNull(crop.laneAt(1000f, 1150f))
    assertFalse(crop.isField(1000f, -1f))
  }

  @Test fun sideKeysAreSlantedAndCannotStealUpperField() {
    val settings = ControlSettings(laneHeight = 0.1f)
    val geometry = TouchGeometry(1920f, 1080f, settings, placeVideo(1920, 1080, 1280, 720, settings))
    assertEquals(0, geometry.laneAt(192f, 820f))
    assertNull(geometry.laneAt(390f, 820f))
    assertTrue(geometry.isField(192f, 680f))
    assertNull(geometry.laneAt(192f, 680f))
    assertEquals(5, geometry.laneAt(1728f, 820f))
  }

  @Test fun tallerButtonsExtendAboveSkyLineWithoutMovingJudgmentsOrOverlappingField() {
    val settings = ControlSettings(laneHeight = 0.4f)
    val geometry = TouchGeometry(1920f, 1080f, settings, placeVideo(1920, 1080, 1280, 720, settings))
    for (x in listOf(192f, 500f, 820f, 1100f, 1400f, 1728f)) {
      assertTrue(geometry.isField(x, 647f))
      assertNull(geometry.laneAt(x, 647f))
      assertTrue(geometry.laneAt(x, 649f) != null)
      assertFalse(geometry.isField(x, 649f))
    }
    assertEquals(685.8f, geometry.fieldLineY, 0.01f)
    assertEquals(961.2f, geometry.laneRegions[2].judgmentLeft, 0.01f)
    assertEquals(783f, geometry.laneRegions[0].judgmentLeft, 0.01f)
    assertEquals(961.2f, geometry.laneRegions[0].judgmentRight, 0.01f)
  }

  @Test fun rejectsCrossedOrOverlappingJudgmentLines() {
    assertFailsWith<IllegalArgumentException> { JudgmentLayout(fieldY = 0.8f) }
    assertFailsWith<IllegalArgumentException> { JudgmentLayout(floorLeft = 0.9f) }
    assertFailsWith<IllegalArgumentException> { JudgmentLayout(sideY = Float.NaN) }
  }
}
