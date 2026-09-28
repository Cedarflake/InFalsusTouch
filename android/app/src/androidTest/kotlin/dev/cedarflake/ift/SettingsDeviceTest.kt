package dev.cedarflake.ift

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.SeekBar
import android.widget.Spinner

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import dev.cedarflake.ift.settings.ControlSettings
import dev.cedarflake.ift.settings.FieldMode
import dev.cedarflake.ift.settings.LayoutMode
import dev.cedarflake.ift.settings.VideoScale
import dev.cedarflake.ift.settings.JudgmentLayout
import dev.cedarflake.ift.settings.VideoPlacement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

import java.util.concurrent.CountDownLatch
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class SettingsDeviceTest {
  @Test fun settingsPersistAcrossStoreInstancesAndRecoverFromCorruption() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val name = "settings-device-test"
    val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    val value = ControlSettings(fieldMode = FieldMode.RELATIVE, layoutMode = LayoutMode.RESERVED,
      videoScale = VideoScale.CROP, laneHeight = 0.35f, fieldHeight = 0.5f, fieldLeft = 0.1f, fieldRight = 0.9f,
      laneOpacity = 0.6f, laneGapDp = 5f, brightness = 0.7f, showLabels = false,
      showStatistics = true, showFieldGuide = true, autoConnect = true, autoHideControls = false, highRefreshDisplay = false,
      judgment = JudgmentLayout(fieldLeft = 0.08f, fieldRight = 0.92f, floorY = 0.88f, sideY = 0.74f))
    try {
      SettingsStore(context, name).use { store ->
        val saved = CountDownLatch(1)
        val success = AtomicBoolean()
        store.save(value) { success.set(it); saved.countDown() }
        assertTrue(saved.await(3, TimeUnit.SECONDS))
        assertTrue(success.get())
      }
      SettingsStore(context, name).use { assertEquals(LoadedSettings(value, false), it.load()) }
      assertTrue(preferences.edit().putFloat("fieldLeft", Float.NaN).commit())
      SettingsStore(context, name).use { assertEquals(LoadedSettings(ControlSettings(), true), it.load()) }
      assertTrue(preferences.edit().putString("fieldLeft", "wrong type").commit())
      SettingsStore(context, name).use { assertTrue(it.load().recovered) }
    } finally { context.deleteSharedPreferences(name) }
  }

  @Test fun settingsDialogValidatesRangeAndSavesAllControls() {
    DeviceActivity.launch().use { scenario ->
      val saved = AtomicReference<ControlSettings?>()
      val closed = AtomicBoolean()
      val shown = AtomicReference<AlertDialog>()
      scenario.onActivity { activity ->
        shown.set(SettingsDialog(activity, ControlSettings(), { saved.set(it) }, {}, {}, { closed.set(true) }).show())
      }
      val instrumentation = InstrumentationRegistry.getInstrumentation()
      instrumentation.waitForIdleSync()
      requireNotNull(instrumentation.uiAutomation.takeScreenshot()).let { screenshot ->
        File(instrumentation.targetContext.filesDir, "settings-screen.png").outputStream().use {
          screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
      }
      scenario.onActivity {
        val dialog = requireNotNull(shown.get())
        try {
          dialog.findViewById<View>(android.R.id.content).apply {
            findViewWithTag<Spinner>("setting-field-mode").setSelection(1)
            findViewWithTag<Spinner>("setting-layout").setSelection(1)
            findViewWithTag<Spinner>("setting-scale").setSelection(2)
            findViewWithTag<SeekBar>("setting-lane-height").progress = 25
            findViewWithTag<SeekBar>("setting-field-height").progress = 40
            findViewWithTag<SeekBar>("setting-field-left").progress = 95
            findViewWithTag<SeekBar>("setting-field-right").progress = 89
            findViewWithTag<CheckBox>("setting-statistics").isChecked = true
            findViewWithTag<CheckBox>("setting-labels").isChecked = false
            findViewWithTag<CheckBox>("setting-auto-connect").isChecked = true
            findViewWithTag<CheckBox>("setting-auto-hide").isChecked = false
            findViewWithTag<CheckBox>("setting-high-refresh").isChecked = false
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            assertEquals(null, saved.get())
            assertTrue(dialog.isShowing)
            findViewWithTag<SeekBar>("setting-field-left").progress = 10
          }
          dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
          assertEquals(ControlSettings(fieldMode = FieldMode.RELATIVE, layoutMode = LayoutMode.RESERVED,
            videoScale = VideoScale.CROP, laneHeight = 0.35f, fieldHeight = 0.5f, fieldLeft = 0.1f, fieldRight = 0.9f,
            showStatistics = true, showLabels = false, autoConnect = true, autoHideControls = false, highRefreshDisplay = false), saved.get())
        } finally { dialog.dismiss() }
      }
      InstrumentationRegistry.getInstrumentation().waitForIdleSync()
      assertTrue(closed.get())
    }
  }

  @Test fun calibrationUsesWholeControllerWidthAndRejectsNarrowRange() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val context = InstrumentationRegistry.getInstrumentation().targetContext
      var left = -1f
      var right = -1f
      val calibration = FieldCalibrationView(context, ControlSettings(), { l, r -> left = l; right = r }, {})
      calibration.measure(View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
      calibration.layout(0, 0, 1000, 600)
      val target = calibration.getChildAt(0)
      val save = calibration.findViewWithTag<Button>("calibration-save")
      fun tap(x: Float) {
        val now = SystemClock.uptimeMillis()
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
          val event = MotionEvent.obtain(now, now, action, x, 300f, 0)
          try { target.dispatchTouchEvent(event) } finally { event.recycle() }
        }
      }
      tap(123f)
      tap(140f)
      assertFalse(save.isEnabled)
      tap(876f)
      assertTrue(save.isEnabled)
      save.performClick()
      assertEquals(0.123f, left, 0.000001f)
      assertEquals(0.876f, right, 0.000001f)
    }
  }

  @Test fun judgmentCalibrationUsesVideoCoordinatesAndIgnoresSideBars() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      val context = InstrumentationRegistry.getInstrumentation().targetContext
      val video = VideoPlacement(100, 50, 1600, 900, 1000)
      val expected = JudgmentLayout()
      var saved: JudgmentLayout? = null
      val calibration = JudgmentCalibrationView(context, { video }, expected, { saved = it }, {})
      calibration.measure(View.MeasureSpec.makeMeasureSpec(1800, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY))
      calibration.layout(0, 0, 1800, 1000)
      val canvas = calibration.getChildAt(0)
      fun tap(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x, y, 0)
        try { canvas.dispatchTouchEvent(event) } finally { event.recycle() }
      }
      tap(50f, 600f)
      for ((x, y) in listOf(expected.fieldLeft to expected.fieldY, expected.fieldRight to expected.fieldY,
        expected.floorLeft to expected.floorY, expected.floorRight to expected.floorY,
        expected.sideLeft to expected.sideY, expected.sideRight to expected.sideY)) {
        tap(video.left + video.width * x, video.top + video.height * y)
      }
      val save = calibration.findViewWithTag<Button>("judgment-save")
      assertTrue(save.isEnabled)
      save.performClick()
      val result = requireNotNull(saved)
      assertEquals(expected.fieldLeft, result.fieldLeft, 0.00001f)
      assertEquals(expected.floorRight, result.floorRight, 0.00001f)
      assertEquals(expected.sideY, result.sideY, 0.00001f)
    }
  }
}
