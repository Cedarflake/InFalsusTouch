package dev.cedarflake.ift

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.Point
import android.os.SystemClock
import android.view.Choreographer
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import dev.cedarflake.ift.settings.ControlSettings
import dev.cedarflake.ift.settings.FieldMode
import dev.cedarflake.ift.settings.LayoutMode
import dev.cedarflake.ift.settings.VideoScale
import dev.cedarflake.ift.settings.JudgmentLayout

import io.flutter.embedding.android.FlutterView

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class SettingsDeviceTest {
  @Test fun legacyFieldModeMigratesWithoutResettingOtherPreferences() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val name = "field-migration-test"
    val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    val original = ControlSettings(language = "zh", theme = "light", controlsMask = 73,
      fieldMode = FieldMode.ABSOLUTE, fieldLeft = 0.12345f, laneHeight = 0.35f)
    fun save(value: ControlSettings) {
      SettingsStore(context, name).use { store ->
        val completed = CountDownLatch(1)
        val success = AtomicBoolean()
        store.save(value) { success.set(it); completed.countDown() }
        assertTrue(completed.await(3, TimeUnit.SECONDS))
        assertTrue(success.get())
      }
    }
    try {
      save(original)
      assertTrue(preferences.edit().remove("fieldInputVersion").commit())
      val migrated = original.copy(fieldMode = FieldMode.RELATIVE)
      SettingsStore(context, name).use { assertEquals(LoadedSettings(migrated, false), it.load()) }
      save(migrated)
      SettingsStore(context, name).use { assertEquals(LoadedSettings(migrated, false), it.load()) }
      save(original)
      SettingsStore(context, name).use { assertEquals(LoadedSettings(original, false), it.load()) }
    } finally { context.deleteSharedPreferences(name) }
  }

  @Test fun settingsPersistAcrossStoreInstancesAndRecoverFromCorruption() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val name = "settings-device-test"
    val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    val value = ControlSettings(language = "zh", theme = "light", controlsMask = 73, fieldMode = FieldMode.RELATIVE, layoutMode = LayoutMode.RESERVED,
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

  @Test fun flutterMaterialSettingsSwitchLanguagesAndPersistAcrossActivityLaunches() {
    DeviceSettings { it.copy(language = "en", laneHeight = 0.4f, autoConnect = false) }.use {
      DeviceActivity.launch().use {
        openSettings()
        screenshot("flutter-connection-en.png")
        click("Touch")
        waitForText("Controller settings")
        screenshot("flutter-settings-en.png")
        click("Other")
        click("中文")
        waitForText("控制设置")
        click("触控")
        screenshot("flutter-settings-zh.png")
        click("连接")
        screenshot("flutter-connection-zh.png")
        SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext).use { store ->
          assertEquals("zh", store.load().value.language)
        }
      }
      DeviceActivity.launch().use {
        openSettings("zh")
        waitForText("控制设置")
        click("其他")
        click("English")
        waitForText("Controller settings")
      }
    }
  }

  @Test fun selectedControlsPersistAndCanAllBeHidden() {
    DeviceSettings { it.copy(language = "en", controlsMask = 127, laneHeight = 0.4f, autoConnect = false) }.use {
      DeviceActivity.launch().use {
        openSettings()
        click("Controls")
        scrollContent()
        waitForText("Choose this device’s controls")
        screenshot("flutter-controls-en.png")
        click("Other")
        click("中文")
        click("按键显示")
        scrollContent()
        waitForText("这台设备显示什么")
        screenshot("flutter-controls-zh.png")
        click("只看画面")
        click("返回游戏")
        waitForText("设置", clickable = true)
        SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext).use { store ->
          assertEquals(0, store.load().value.controlsMask)
        }
      }
      DeviceActivity.launch().use {
        openSettings("zh")
        click("按键显示")
        scrollContent()
        click("仅 Field")
        click("返回游戏")
        waitForText("设置", clickable = true)
        SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext).use { store ->
          assertEquals(64, store.load().value.controlsMask)
        }
      }
    }
  }

  @Suppress("DEPRECATION")
  @Test fun fullScreenFlutterSurfaceDoesNotResizeWhenOpeningOrClosingSettings() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    DeviceSettings { it.copy(language = "en", controlsMask = 127, laneHeight = 0.4f, autoConnect = false) }.use {
      DeviceActivity.launch().use { scenario ->
        waitForText("Settings", clickable = true)
        val frames = mutableListOf<Pair<Int, Int>>()
        val physical = Point()
        val finished = CountDownLatch(1)
        scenario.onActivity { activity ->
          activity.window.decorView.display.getRealSize(physical)
          val content = activity.findViewById<ViewGroup>(android.R.id.content)
          val root = content.getChildAt(0) as ControllerRoot
          val flutter = root.interfaceView as FlutterView
          assertEquals(physical.x, root.width)
          assertEquals(physical.y, root.height)
          Choreographer.getInstance().postFrameCallback(object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
              frames += flutter.width to flutter.height
              if (frames.size < 90) Choreographer.getInstance().postFrameCallback(this) else finished.countDown()
            }
          })
        }
        openSettings()
        waitForText("Controller settings")
        click("Back to game")
        waitForText("Settings", clickable = true)
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        assertEquals("The Flutter texture resized during a panel transition", setOf(physical.x to physical.y), frames.toSet())
        screenshot("flutter-fullscreen-controls.png")
      }
    }
  }

  @Test fun settingsEntryHasEqualMarginsAndRequiresASecondTap() {
    DeviceSettings { it.copy(language = "zh", controlsMask = 127, laneHeight = 0.4f, autoConnect = false) }.use {
      DeviceActivity.launch().use { scenario ->
        val entry = Rect()
        waitForText("设置", clickable = true).getBoundsInScreen(entry)
        assertEquals("Settings entry must have equal left and top margins", entry.left, entry.top)
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val toast = CountDownLatch(1)
        automation.setOnAccessibilityEventListener { event ->
          if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
            event.className == "android.widget.Toast" && event.text.any { it.contains("再按一下进入设置") }) toast.countDown()
        }
        try {
          click("设置")
          assertTrue("The first tap must show an Android system Toast", toast.await(3, TimeUnit.SECONDS))
        } finally { automation.setOnAccessibilityEventListener(null) }
        SystemClock.sleep(250)
        scenario.onActivity { assertEquals("compact", it.uiSnapshot()["panel"]) }
        screenshot("flutter-settings-hint-zh.png")
        SystemClock.sleep(2100)
        click("设置")
        SystemClock.sleep(100)
        scenario.onActivity { assertEquals("compact", it.uiSnapshot()["panel"]) }
        click("设置")
        waitForText("控制设置")
        SystemClock.sleep(500)
        scenario.onActivity { assertEquals("settings", it.uiSnapshot()["panel"]) }
      }
    }
  }

  @Test fun judgmentCalibrationWithoutVideoUsesLocalizedSystemToast() {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    for ((language, message) in listOf("zh" to "请先连接 USB，等待游戏画面显示后再校准。",
      "en" to "Connect USB and wait for the game picture before calibrating.")) {
      DeviceSettings { it.copy(language = language, autoConnect = false) }.use {
        DeviceActivity.launch().use { scenario ->
          scenario.onActivity { it.setPanel("settings") }
          val toast = CountDownLatch(1)
          automation.setOnAccessibilityEventListener { event ->
            if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
              event.className == "android.widget.Toast" && event.text.any { it.toString() == message }) toast.countDown()
          }
          try {
            scenario.onActivity { activity ->
              val keys = setOf("panel", "connection", "detail", "notice")
              val before = activity.uiSnapshot().filterKeys { it in keys }
              assertEquals(null, activity.videoSnapshot)
              activity.setPanel("calibrateJudgment")
              assertEquals("A calibration hint must not change connection or panel state", before,
                activity.uiSnapshot().filterKeys { it in keys })
              activity.setPanel("compact")
            }
            assertTrue("Calibration must show the $language Android system Toast", toast.await(3, TimeUnit.SECONDS))
          } finally { automation.setOnAccessibilityEventListener(null) }
        }
      }
    }
  }

  private fun openSettings(language: String = "en") {
    val label = if (language == "zh") "设置" else "Settings"
    click(label)
    SystemClock.sleep(100)
    click(label)
    waitForText(if (language == "zh") "控制设置" else "Controller settings")
  }

  private fun click(label: String) {
    val node = waitForText(label, clickable = true)
    val bounds = Rect()
    node.getBoundsInScreen(bounds)
    assertTrue("Flutter control is off screen: " + label, !bounds.isEmpty)
    tap(bounds)
  }

  private fun scrollContent() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val root = requireNotNull(instrumentation.uiAutomation.rootInActiveWindow)
    val screen = Rect().also(root::getBoundsInScreen)
    fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
      val bounds = Rect().also(node::getBoundsInScreen)
      if (node.isScrollable && bounds.centerX() > screen.centerX()) return node
      for (index in 0 until node.childCount) {
        node.getChild(index)?.let { find(it)?.let { match -> return match } }
      }
      return null
    }
    val content = requireNotNull(find(root)) { "Settings content must be scrollable" }
    assertTrue(content.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))
    instrumentation.waitForIdleSync()
  }

  private fun tap(bounds: Rect) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val start = SystemClock.uptimeMillis()
    for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
      val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, bounds.exactCenterX(), bounds.exactCenterY(), 0)
      event.source = InputDevice.SOURCE_TOUCHSCREEN
      try { instrumentation.sendPointerSync(event) } finally { event.recycle() }
    }
    instrumentation.waitForIdleSync()
  }

  private fun waitForText(label: String, clickable: Boolean = false): AccessibilityNodeInfo {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
      for (index in 0 until node.childCount) {
        node.getChild(index)?.let { child -> find(child)?.let { return it } }
      }
      val text = (node.text ?: node.contentDescription)?.toString().orEmpty()
      return node.takeIf { label in text.lines() && (!clickable || it.isClickable) }
    }
    val deadline = SystemClock.uptimeMillis() + 20_000
    while (SystemClock.uptimeMillis() < deadline) {
      automation.rootInActiveWindow?.let { find(it)?.let { match -> return match } }
      SystemClock.sleep(100)
    }
    error("Flutter control did not appear: " + label)
  }

  private fun screenshot(name: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    SystemClock.sleep(200)
    requireNotNull(instrumentation.uiAutomation.takeScreenshot()).let { bitmap ->
      try {
        File(instrumentation.targetContext.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
      } finally { bitmap.recycle() }
    }
  }
}
