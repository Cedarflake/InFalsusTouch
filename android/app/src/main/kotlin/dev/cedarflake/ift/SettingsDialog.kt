package dev.cedarflake.ift

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView

import dev.cedarflake.ift.settings.ControlSettings
import dev.cedarflake.ift.settings.FieldMode
import dev.cedarflake.ift.settings.LayoutMode
import dev.cedarflake.ift.settings.VideoScale

class SettingsDialog(
  private val context: Context,
  private val current: ControlSettings,
  private val save: (ControlSettings) -> Unit,
  private val calibrate: () -> Unit,
  private val align: () -> Unit,
  private val closed: () -> Unit,
) {
  private val body = LinearLayout(context).apply {
    orientation = LinearLayout.VERTICAL
    val padding = (20 * resources.displayMetrics.density).toInt()
    setPadding(padding, 8, padding, 8)
  }
  private val errorText = TextView(context).apply { setTextColor(Color.rgb(255, 150, 150)) }

  fun show(): AlertDialog {
    val fieldMode = selector(R.string.field_mode, "setting-field-mode", FieldMode.entries.map { it.name }, current.fieldMode.ordinal)
    val layout = selector(R.string.layout_mode, "setting-layout", context.resources.getStringArray(R.array.layout_options).toList(), current.layoutMode.ordinal)
    val scale = selector(R.string.video_scale, "setting-scale", context.resources.getStringArray(R.array.video_scale_options).toList(), current.videoScale.ordinal)
    note(R.string.scale_help)
    val highRefresh = toggle(R.string.high_refresh_display, "setting-high-refresh", current.highRefreshDisplay)
    note(R.string.high_refresh_help)
    val laneHeight = slider(R.string.lane_height, "setting-lane-height", 10, 50, (current.laneHeight * 100).toInt(), "%")
    val fieldHeight = slider(R.string.field_height, "setting-field-height", 10, 100, (current.fieldHeight * 100).toInt(), "%")
    val fieldLeft = slider(R.string.touch_left, "setting-field-left", 0, 99, (current.fieldLeft * 100).toInt(), "%")
    val fieldRight = slider(R.string.touch_right, "setting-field-right", 1, 100, (current.fieldRight * 100).toInt(), "%")
    note(R.string.fixed_layout_help)
    val opacity = slider(R.string.lane_opacity, "setting-opacity", 0, 100, (current.laneOpacity * 100).toInt(), "%")
    val gap = slider(R.string.lane_gap, "setting-gap", 0, 20, current.laneGapDp.toInt(), "dp")
    val brightness = slider(R.string.lane_brightness, "setting-brightness", 10, 100, (current.brightness * 100).toInt(), "%")
    val labels = toggle(R.string.show_labels, "setting-labels", current.showLabels)
    val statistics = toggle(R.string.show_statistics, "setting-statistics", current.showStatistics)
    val guide = toggle(R.string.show_field_guide, "setting-field-guide", current.showFieldGuide)
    val autoConnect = toggle(R.string.auto_connect, "setting-auto-connect", current.autoConnect)
    val autoHide = toggle(R.string.auto_hide_controls, "setting-auto-hide", current.autoHideControls)
    note(R.string.usb_discovery_help)
    val calibrateButton = Button(context).apply { setText(R.string.calibrate_touch); tag = "calibrate-touch" }
    val alignButton = Button(context).apply { setText(R.string.calibrate_judgment); tag = "calibrate-judgment" }
    body.addView(alignButton)
    note(R.string.judgment_help)
    body.addView(calibrateButton)
    note(R.string.host_calibration_help)
    fun updateLayoutControls() {
      val isFixed = LayoutMode.entries[layout.selectedItemPosition] != LayoutMode.ALIGNED
      fieldLeft.bar.isEnabled = isFixed
      fieldRight.bar.isEnabled = isFixed
      calibrateButton.isEnabled = isFixed
    }
    layout.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
      override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { updateLayoutControls() }
      override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }
    updateLayoutControls()
    body.addView(errorText)
    val dialog = AlertDialog.Builder(context)
      .setTitle(R.string.settings)
      .setView(ScrollView(context).apply { addView(body) })
      .setNegativeButton(android.R.string.cancel, null)
      .setNeutralButton(R.string.reset_settings) { _, _ -> save(ControlSettings()) }
      .setPositiveButton(R.string.save_settings, null)
      .create()
    dialog.setOnDismissListener { closed() }
    fun saveValues(): Boolean {
      val left = fieldLeft.scaledValue(current.fieldLeft)
      val right = fieldRight.scaledValue(current.fieldRight)
      if (left >= right) {
        errorText.setText(R.string.invalid_field_range)
        errorText.requestFocus()
        return false
      }
      save(current.copy(
        fieldMode = FieldMode.entries[fieldMode.selectedItemPosition],
        layoutMode = LayoutMode.entries[layout.selectedItemPosition],
        videoScale = VideoScale.entries[scale.selectedItemPosition],
        laneHeight = laneHeight.scaledValue(current.laneHeight), fieldHeight = fieldHeight.scaledValue(current.fieldHeight),
        fieldLeft = left, fieldRight = right,
        laneOpacity = opacity.scaledValue(current.laneOpacity), laneGapDp = gap.scaledValue(current.laneGapDp, 1f),
        brightness = brightness.scaledValue(current.brightness),
        showLabels = labels.isChecked, showStatistics = statistics.isChecked, showFieldGuide = guide.isChecked,
        autoConnect = autoConnect.isChecked, autoHideControls = autoHide.isChecked,
        highRefreshDisplay = highRefresh.isChecked,
      ))
      return true
    }
    calibrateButton.setOnClickListener {
      if (saveValues()) { dialog.dismiss(); calibrate() }
    }
    alignButton.setOnClickListener {
      if (saveValues()) { dialog.dismiss(); align() }
    }
    dialog.setOnShowListener {
      dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
        if (saveValues()) dialog.dismiss()
      }
    }
    dialog.show()
    return dialog
  }

  private fun selector(label: Int, id: String, values: List<String>, selected: Int): Spinner {
    body.addView(TextView(context).apply { setText(label); setPadding(0, 12, 0, 0) })
    return Spinner(context).apply {
      tag = id
      adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, values)
      setSelection(selected)
      body.addView(this)
    }
  }

  private class Slider(val bar: SeekBar, val minimum: Int) {
    private val initialProgress = bar.progress
    val value: Int get() = bar.progress + minimum
    fun scaledValue(original: Float, divisor: Float = 100f): Float =
      if (bar.progress == initialProgress) original else value / divisor
  }

  private fun slider(label: Int, id: String, minimum: Int, maximum: Int, value: Int, unit: String): Slider {
    val description = TextView(context)
    val title = context.getString(label)
    body.addView(description)
    val bar = SeekBar(context).apply {
      tag = id
      max = maximum - minimum
      progress = value - minimum
      contentDescription = title
    }
    val result = Slider(bar, minimum)
    fun updateLabel() { description.text = context.getString(R.string.setting_value, title, result.value, unit) }
    bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
      override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) { updateLabel() }
      override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
      override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
    })
    updateLabel()
    body.addView(bar)
    return result
  }

  private fun toggle(label: Int, id: String, value: Boolean): CheckBox = CheckBox(context).apply {
    tag = id
    setText(label)
    isChecked = value
    body.addView(this)
  }

  private fun note(text: Int) {
    body.addView(TextView(context).apply { setText(text); textSize = 12f; setPadding(0, 8, 0, 12) })
  }
}
