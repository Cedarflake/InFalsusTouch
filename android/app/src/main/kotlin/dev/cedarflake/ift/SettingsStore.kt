package dev.cedarflake.ift

import android.content.Context
import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper

import dev.cedarflake.ift.settings.ControlSettings
import dev.cedarflake.ift.settings.FieldMode
import dev.cedarflake.ift.settings.LayoutMode
import dev.cedarflake.ift.settings.VideoScale

import java.io.Closeable
import java.util.concurrent.Executors

data class LoadedSettings(val value: ControlSettings, val recovered: Boolean)

class SettingsStore(context: Context, name: String = "controller-settings") : Closeable {
  private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
  private val writer = Executors.newSingleThreadExecutor { task -> Thread(task, "ift-settings") }
  private val callbacks = Handler(Looper.getMainLooper())

  fun load(): LoadedSettings {
    val defaults = ControlSettings()
    return try {
      LoadedSettings(ControlSettings(
        fieldMode = FieldMode.valueOf(preferences.getString("fieldMode", defaults.fieldMode.name) ?: defaults.fieldMode.name),
        layoutMode = LayoutMode.valueOf(preferences.getString("layoutMode", defaults.layoutMode.name) ?: defaults.layoutMode.name),
        videoScale = VideoScale.valueOf(preferences.getString("videoScale", defaults.videoScale.name) ?: defaults.videoScale.name),
        laneHeight = preferences.getFloat("laneHeight", defaults.laneHeight),
        fieldHeight = preferences.getFloat("fieldHeight", defaults.fieldHeight),
        fieldLeft = preferences.getFloat("fieldLeft", defaults.fieldLeft),
        fieldRight = preferences.getFloat("fieldRight", defaults.fieldRight),
        laneOpacity = preferences.getFloat("laneOpacity", defaults.laneOpacity),
        laneGapDp = preferences.getFloat("laneGapDp", defaults.laneGapDp),
        brightness = preferences.getFloat("brightness", defaults.brightness),
        showLabels = preferences.getBoolean("showLabels", defaults.showLabels),
        showStatistics = preferences.getBoolean("showStatistics", defaults.showStatistics),
        showFieldGuide = preferences.getBoolean("showFieldGuide", defaults.showFieldGuide),
        autoConnect = preferences.getBoolean("autoConnect", defaults.autoConnect),
        autoHideControls = preferences.getBoolean("autoHideControls", defaults.autoHideControls),
      ), false)
    } catch (_: IllegalArgumentException) {
      LoadedSettings(defaults, true)
    } catch (_: ClassCastException) {
      LoadedSettings(defaults, true)
    }
  }

  // Commit runs on the writer thread so persistence failures can be reported.
  @SuppressLint("ApplySharedPref")
  fun save(value: ControlSettings, completed: (Boolean) -> Unit) {
    writer.execute {
      val success = try { preferences.edit()
        .putString("fieldMode", value.fieldMode.name)
        .putString("layoutMode", value.layoutMode.name)
        .putString("videoScale", value.videoScale.name)
        .putFloat("laneHeight", value.laneHeight)
        .putFloat("fieldHeight", value.fieldHeight)
        .putFloat("fieldLeft", value.fieldLeft)
        .putFloat("fieldRight", value.fieldRight)
        .putFloat("laneOpacity", value.laneOpacity)
        .putFloat("laneGapDp", value.laneGapDp)
        .putFloat("brightness", value.brightness)
        .putBoolean("showLabels", value.showLabels)
        .putBoolean("showStatistics", value.showStatistics)
        .putBoolean("showFieldGuide", value.showFieldGuide)
        .putBoolean("autoConnect", value.autoConnect)
        .putBoolean("autoHideControls", value.autoHideControls)
        .commit() } catch (_: RuntimeException) { false }
      callbacks.post { completed(success) }
    }
  }

  override fun close() { writer.shutdown() }
}
