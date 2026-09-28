package dev.cedarflake.ift.touch

import dev.cedarflake.ift.settings.ControlSettings
import dev.cedarflake.ift.settings.JudgmentLayout
import dev.cedarflake.ift.settings.LayoutMode
import dev.cedarflake.ift.settings.VideoScale
import dev.cedarflake.ift.settings.placeVideo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlignedTouchTest {
  @Test fun partialSelectionsCenterWithoutResizingShapesOrChangingLaneIds() {
    for (mode in LayoutMode.entries) {
      val settings = ControlSettings(layoutMode = mode, laneHeight = 0.1f)
      val video = placeVideo(2400, 1080, 1280, 720, settings)
      val original = TouchGeometry(2400f, 1080f, settings, video)
      for (mask in 1 until 63) {
        val geometry = TouchGeometry(2400f, 1080f, settings.copy(controlsMask = mask or 64), video)
        val selected = (0..5).filter { mask and (1 shl it) != 0 }
        val first = geometry.laneRegions[selected.first()]
        val last = geometry.laneRegions[selected.last()]
        assertEquals(1200f, (first.left + last.right) / 2f, 0.001f)
        assertNull(geometry.laneAt(first.left - 1f, 1070f))
        assertNull(geometry.laneAt(last.right + 1f, 1070f))
        for (lane in selected) {
          val region = geometry.laneRegions[lane]
          val previous = original.laneRegions[lane]
          assertEquals(previous.right - previous.left, region.right - region.left, 0.001f)
          assertEquals(previous.topLeft, region.topLeft)
          assertEquals(previous.topRight, region.topRight)
          assertEquals(previous.bottom, region.bottom)
          assertEquals(previous.judgmentLeft, region.judgmentLeft)
          assertEquals(previous.judgmentRight, region.judgmentRight)
          val x = (region.left + region.right) / 2f
          val y = (maxOf(region.topLeft, region.topRight) + region.bottom) / 2f
          assertEquals(lane, geometry.laneAt(x, y))
          assertFalse(geometry.isField(x, y))
        }
        assertEquals(original.fieldLeft, geometry.fieldLeft)
        assertEquals(original.fieldRight, geometry.fieldRight)
      }
    }
  }

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
