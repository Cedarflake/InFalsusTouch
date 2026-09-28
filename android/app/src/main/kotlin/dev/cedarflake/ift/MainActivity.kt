package dev.cedarflake.ift

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.cedarflake.ift.settings.ControlSettings
import dev.cedarflake.ift.settings.FieldMode
import dev.cedarflake.ift.touch.TouchSink
import dev.cedarflake.ift.transport.ConnectionState
import dev.cedarflake.ift.transport.ControlClient
import dev.cedarflake.ift.transport.ControlListener
import dev.cedarflake.ift.transport.MessageType
import java.util.concurrent.Executor

class MainActivity : Activity() {
  private lateinit var controllerView: ControllerView
  private lateinit var connectButton: Button
  private lateinit var modeButton: Button
  private lateinit var statusText: TextView
  private lateinit var client: ControlClient
  private var state = ConnectionState.DISCONNECTED
  private var settings = ControlSettings()
  private var isTargetReady = false
  private var inputRtt = 0.0

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    client = ControlClient(object : ControlListener {
      override fun onState(state: ConnectionState, detail: String) {
        this@MainActivity.state = state
        connectButton.setText(if (state == ConnectionState.DISCONNECTED) R.string.connect else R.string.disconnect)
        statusText.text = detail
        if (state == ConnectionState.DISCONNECTED) setTargetReady(false)
      }
      override fun onTargetReady(ready: Boolean) { setTargetReady(ready) }
      override fun onRtt(milliseconds: Double) {
        inputRtt = milliseconds
        updateStatus()
      }
    }, Executor { runOnUiThread(it) })
    createViews()
    enterImmersiveMode()
  }

  private fun createViews() {
    val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
    statusText = TextView(this).apply {
      setText(R.string.status_idle)
      gravity = android.view.Gravity.CENTER_VERTICAL
      setPadding(16, 0, 8, 0)
      textSize = 12f
    }
    connectButton = Button(this).apply {
      setText(R.string.connect)
      setOnClickListener {
        controllerView.releaseTouches()
        if (state == ConnectionState.DISCONNECTED) client.connect() else client.close()
      }
    }
    modeButton = Button(this).apply {
      setText(R.string.absolute)
      setOnClickListener {
        val mode = if (settings.fieldMode == FieldMode.ABSOLUTE) FieldMode.RELATIVE else FieldMode.ABSOLUTE
        settings = settings.copy(fieldMode = mode)
        controllerView.setSettings(settings)
        setText(if (mode == FieldMode.ABSOLUTE) R.string.absolute else R.string.relative)
      }
    }
    controllerView = ControllerView(this, object : TouchSink {
      override fun laneDown(lane: Int) { client.send(MessageType.LANE_DOWN, lane) }
      override fun laneUp(lane: Int) { client.send(MessageType.LANE_UP, lane) }
      override fun fieldAbsolute(x: Float) { client.send(MessageType.FIELD_ABSOLUTE, value = x) }
      override fun fieldRelative(deltaX: Float) { client.send(MessageType.FIELD_RELATIVE, value = deltaX) }
      override fun releaseAll() { client.send(MessageType.RELEASE_ALL) }
    })
    toolbar.addView(statusText, LinearLayout.LayoutParams(0, -1, 1f))
    toolbar.addView(modeButton)
    toolbar.addView(connectButton)
    root.addView(toolbar, LinearLayout.LayoutParams(-1, (48 * resources.displayMetrics.density).toInt()))
    root.addView(controllerView, LinearLayout.LayoutParams(-1, 0, 1f))
    root.setOnApplyWindowInsetsListener { view, insets ->
      if (Build.VERSION.SDK_INT >= 30) {
        val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
      }
      insets
    }
    setContentView(root)
  }

  private fun setTargetReady(ready: Boolean) {
    isTargetReady = ready
    controllerView.setInputAllowed(ready && hasWindowFocus())
    updateStatus()
  }

  private fun updateStatus() {
    if (state != ConnectionState.CONNECTED) return
    statusText.text = if (isTargetReady) getString(R.string.status_ready, inputRtt) else getString(R.string.status_waiting)
  }

  @Suppress("DEPRECATION")
  private fun enterImmersiveMode() {
    if (Build.VERSION.SDK_INT >= 30) {
      window.insetsController?.apply {
        hide(WindowInsets.Type.systemBars())
        systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      }
    } else {
      window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (::controllerView.isInitialized) controllerView.setInputAllowed(hasFocus && isTargetReady)
    if (hasFocus) enterImmersiveMode()
  }

  override fun onPause() {
    controllerView.releaseTouches()
    client.close()
    super.onPause()
  }

  override fun onDestroy() {
    client.close()
    super.onDestroy()
  }
}
