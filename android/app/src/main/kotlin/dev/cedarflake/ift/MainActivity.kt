package dev.cedarflake.ift

import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.SurfaceHolder
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

import dev.cedarflake.ift.settings.ControlSettings
import dev.cedarflake.ift.settings.LayoutMode
import dev.cedarflake.ift.touch.TouchSink
import dev.cedarflake.ift.transport.ConnectionState
import dev.cedarflake.ift.transport.ControlClient
import dev.cedarflake.ift.transport.ControlListener
import dev.cedarflake.ift.transport.MessageType
import dev.cedarflake.ift.video.VideoClient
import dev.cedarflake.ift.video.VideoListener
import dev.cedarflake.ift.video.VideoSnapshot

import java.util.Locale
import java.util.concurrent.Executor

class MainActivity : Activity(), SurfaceHolder.Callback {
  private lateinit var controllerView: ControllerView
  private lateinit var connectButton: Button
  private lateinit var root: FrameLayout
  private lateinit var toolbar: LinearLayout
  private lateinit var menuButton: Button
  private lateinit var statusText: TextView
  private lateinit var client: ControlClient
  private lateinit var videoClient: VideoClient
  private lateinit var viewport: VideoViewport
  private lateinit var videoStatus: TextView
  private lateinit var settingsStore: SettingsStore
  private val ui = Handler(Looper.getMainLooper())
  private var calibration: FrameLayout? = null
  private var settingsDialog: AlertDialog? = null
  private var isConfiguring = false
  private var isResumed = false
  private var wantsConnection = false
  private var retryDelay = 500L
  private val retryConnection = Runnable {
    if (isResumed && wantsConnection && state == ConnectionState.DISCONNECTED) client.connect()
  }
  private val hideToolbar = Runnable {
    if (state == ConnectionState.CONNECTED && settings.autoHideControls && !isConfiguring) {
      toolbar.visibility = View.GONE
      menuButton.visibility = View.VISIBLE
    }
  }
  private var isVideoStarted = false
  @Volatile var videoSnapshot: VideoSnapshot? = null
    private set
  private var state = ConnectionState.DISCONNECTED
  private var settings = ControlSettings()
  private var isTargetReady = false
  private var inputRtt = 0.0

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    if (Build.VERSION.SDK_INT >= 28) {
      window.attributes = window.attributes.apply {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
      }
    }
    settingsStore = SettingsStore(this)
    val loaded = settingsStore.load()
    settings = loaded.value
    client = ControlClient(object : ControlListener {
      override fun onState(state: ConnectionState, detail: String) {
        if (isDestroyed) return
        this@MainActivity.state = state
        connectButton.setText(if (state == ConnectionState.DISCONNECTED) R.string.connect else R.string.disconnect)
        statusText.text = detail
        if (state == ConnectionState.DISCONNECTED) {
          setTargetReady(false)
          stopVideo()
          showToolbar()
          if (isResumed && wantsConnection && settings.autoConnect) {
            ui.removeCallbacks(retryConnection)
            ui.postDelayed(retryConnection, retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(4000)
          }
        } else if (state == ConnectionState.CONNECTED) {
          retryDelay = 500
          startVideo()
          showToolbar()
        }
      }
      override fun onTargetReady(ready: Boolean) { setTargetReady(ready) }
      override fun onRtt(milliseconds: Double) {
        inputRtt = milliseconds
        updateStatus()
      }
    }, Executor { runOnUiThread(it) })
    videoClient = VideoClient(object : VideoListener {
      override fun onStatus(detail: String) {
        videoStatus.text = detail
        videoStatus.visibility = View.VISIBLE
        videoSnapshot = null
        controllerView.setVideoVisible(false)
      }
      override fun onFormat(width: Int, height: Int) { viewport.setFormat(width, height) }
      override fun onStatistics(snapshot: VideoSnapshot) {
        videoStatus.visibility = if (settings.showStatistics) View.VISIBLE else View.GONE
        videoSnapshot = snapshot
        controllerView.setVideoVisible(snapshot.presentedFrames > 0)
        videoStatus.text = String.format(Locale.US,
          "Display %.0f Hz · USB %.0f fps · Decode %.0f · Present %.0f · %.1f Mbps · Queue %d · Drop %d\nPC capture→encode %.1f ms · Phone receive→present %.1f ms",
          root.display?.mode?.refreshRate ?: 0f, snapshot.receiveFps, snapshot.decodeFps, snapshot.presentFps, snapshot.megabitsPerSecond,
          snapshot.queueDepth, snapshot.droppedFrames, snapshot.captureToEncodeMs, snapshot.receiveToPresentMs)
      }
    }, Executor { runOnUiThread(it) })
    createViews()
    applySettings(settings)
    if (loaded.recovered) Toast.makeText(this, R.string.settings_recovered, Toast.LENGTH_LONG).show()
    enterImmersiveMode()
  }

  private fun createViews() {
    root = FrameLayout(this)
    toolbar = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      setBackgroundColor(0xe610151c.toInt())
    }
    statusText = TextView(this).apply {
      setText(R.string.status_idle)
      gravity = android.view.Gravity.CENTER_VERTICAL
      setPadding(16, 0, 8, 0)
      textSize = 12f
      setTextColor(Color.WHITE)
    }
    connectButton = Button(this).apply {
      tag = "connect"
      setText(R.string.connect)
      setOnClickListener {
        controllerView.releaseTouches()
        ui.removeCallbacks(retryConnection)
        if (state == ConnectionState.DISCONNECTED) {
          wantsConnection = true
          retryDelay = 500
          client.connect()
        } else {
          wantsConnection = false
          client.close()
        }
      }
    }
    val settingsButton = Button(this).apply {
      tag = "settings"
      setText(R.string.settings)
      setOnClickListener { openSettings() }
    }
    menuButton = Button(this).apply {
      tag = "menu"
      setText(R.string.menu_icon)
      contentDescription = getString(R.string.menu)
      textSize = 20f
      setPadding(0, 0, 0, 0)
      visibility = View.GONE
      setOnClickListener { showToolbar() }
    }
    controllerView = ControllerView(this, object : TouchSink {
      override fun laneDown(lane: Int) { client.send(MessageType.LANE_DOWN, lane) }
      override fun laneUp(lane: Int) { client.send(MessageType.LANE_UP, lane) }
      override fun fieldAbsolute(x: Float) { client.send(MessageType.FIELD_ABSOLUTE, value = x) }
      override fun fieldRelative(deltaX: Float) { client.send(MessageType.FIELD_RELATIVE, value = deltaX) }
      override fun releaseAll() { client.send(MessageType.RELEASE_ALL) }
    })
    controllerView.tag = "controller"
    viewport = VideoViewport(this)
    viewport.onPlacement = controllerView::setVideoPlacement
    viewport.surface.holder.addCallback(this)
    videoStatus = TextView(this).apply {
      text = getString(R.string.video_idle)
      textSize = 11f
      setTextColor(android.graphics.Color.WHITE)
      setBackgroundColor(0x99000000.toInt())
      setPadding(12, 4, 12, 4)
      isClickable = false
    }
    val toolbarHeight = (48 * resources.displayMetrics.density).toInt()
    root.addView(viewport, FrameLayout.LayoutParams(-1, -1))
    root.addView(controllerView, FrameLayout.LayoutParams(-1, -1))
    root.addView(videoStatus, FrameLayout.LayoutParams(-2, -2).apply { topMargin = toolbarHeight })
    toolbar.addView(statusText, LinearLayout.LayoutParams(0, -1, 1f))
    toolbar.addView(settingsButton)
    toolbar.addView(connectButton)
    root.addView(toolbar, FrameLayout.LayoutParams(-1, toolbarHeight))
    root.addView(menuButton, FrameLayout.LayoutParams(toolbarHeight, toolbarHeight, Gravity.TOP or Gravity.END))
    root.setOnApplyWindowInsetsListener { view, insets ->
      if (Build.VERSION.SDK_INT >= 30) {
        val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
        val horizontal = maxOf(safe.left, safe.right)
        val vertical = maxOf(safe.top, safe.bottom)
        view.setPadding(horizontal, vertical, horizontal, vertical)
      } else if (Build.VERSION.SDK_INT >= 28) {
        val cutout = insets.displayCutout
        val horizontal = maxOf(cutout?.safeInsetLeft ?: 0, cutout?.safeInsetRight ?: 0)
        val vertical = maxOf(cutout?.safeInsetTop ?: 0, cutout?.safeInsetBottom ?: 0)
        view.setPadding(horizontal, vertical, horizontal, vertical)
      }
      insets
    }
    setContentView(root)
  }

  private fun setTargetReady(ready: Boolean) {
    isTargetReady = ready
    updateInputAllowed()
    updateStatus()
  }

  private fun updateInputAllowed() {
    controllerView.setInputAllowed(isTargetReady && hasWindowFocus() && isResumed && !isConfiguring)
  }

  private fun showToolbar() {
    ui.removeCallbacks(hideToolbar)
    if (calibration != null) return
    toolbar.visibility = View.VISIBLE
    menuButton.visibility = View.GONE
    if (settings.autoHideControls && state == ConnectionState.CONNECTED && !isConfiguring) {
      ui.postDelayed(hideToolbar, 4000)
    }
  }

  private fun applySettings(value: ControlSettings) {
    settings = value
    controllerView.setSettings(value)
    viewport.setSettings(value)
    window.attributes = window.attributes.apply { preferredRefreshRate = if (value.highRefreshDisplay) 120f else 0f }
    videoStatus.visibility = if (value.showStatistics) View.VISIBLE else View.GONE
    showToolbar()
  }

  private fun saveSettings(value: ControlSettings) {
    val enableAutoConnect = !settings.autoConnect && value.autoConnect
    applySettings(value)
    settingsStore.save(value) { success ->
      if (!success && !isDestroyed) Toast.makeText(this, R.string.settings_save_failed, Toast.LENGTH_LONG).show()
    }
    if (!value.autoConnect) ui.removeCallbacks(retryConnection)
    if (enableAutoConnect && isResumed && state == ConnectionState.DISCONNECTED) {
      wantsConnection = true
      ui.post(retryConnection)
    }
  }

  private fun openSettings() {
    if (isConfiguring) return
    isConfiguring = true
    updateInputAllowed()
    showToolbar()
    settingsDialog = SettingsDialog(this, settings, ::saveSettings, ::startCalibration, ::startJudgmentCalibration) {
      settingsDialog = null
      isConfiguring = calibration != null
      updateInputAllowed()
      showToolbar()
    }.show()
  }

  private fun startCalibration() {
    isConfiguring = true
    updateInputAllowed()
    ui.removeCallbacks(hideToolbar)
    toolbar.visibility = View.GONE
    menuButton.visibility = View.GONE
    calibration = FieldCalibrationView(this, settings, { left, right ->
      saveSettings(settings.copy(fieldLeft = left, fieldRight = right))
      finishCalibration()
    }, ::finishCalibration).also { root.addView(it, FrameLayout.LayoutParams(-1, -1)) }
  }

  private fun startJudgmentCalibration() {
    if (videoSnapshot?.presentedFrames == null || viewport.placement == null) {
      Toast.makeText(this, R.string.judgment_needs_video, Toast.LENGTH_LONG).show()
      return
    }
    isConfiguring = true
    updateInputAllowed()
    ui.removeCallbacks(hideToolbar)
    toolbar.visibility = View.GONE
    menuButton.visibility = View.GONE
    calibration = JudgmentCalibrationView(this, { viewport.placement }, settings.judgment, { value ->
      saveSettings(settings.copy(layoutMode = LayoutMode.ALIGNED, judgment = value))
      finishCalibration()
    }, ::finishCalibration).also { root.addView(it, FrameLayout.LayoutParams(-1, -1)) }
  }

  private fun finishCalibration() {
    calibration?.let { root.removeView(it) }
    calibration = null
    isConfiguring = false
    updateInputAllowed()
    showToolbar()
  }

  private fun startVideo() {
    if (state != ConnectionState.CONNECTED || isVideoStarted || !viewport.surface.holder.surface.isValid) return
    isVideoStarted = true
    videoClient.connect(viewport.surface.holder.surface)
  }

  private fun stopVideo() {
    videoClient.close()
    isVideoStarted = false
    videoSnapshot = null
    controllerView.setVideoVisible(false)
    videoStatus.setText(R.string.video_idle)
    videoStatus.visibility = if (settings.showStatistics) View.VISIBLE else View.GONE
  }

  override fun surfaceCreated(holder: SurfaceHolder) { startVideo() }
  override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
  override fun surfaceDestroyed(holder: SurfaceHolder) { stopVideo() }

  private fun updateStatus() {
    if (state != ConnectionState.CONNECTED) return
    statusText.text = if (isTargetReady) getString(R.string.status_ready, inputRtt) else getString(R.string.status_waiting)
  }

  @Suppress("DEPRECATION")
  private fun enterImmersiveMode() {
    if (Build.VERSION.SDK_INT >= 30) {
      window.setDecorFitsSystemWindows(false)
      window.insetsController?.apply {
        hide(WindowInsets.Type.systemBars())
        systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      }
    } else {
      window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (::controllerView.isInitialized) updateInputAllowed()
    if (hasFocus) enterImmersiveMode()
  }

  override fun onResume() {
    super.onResume()
    isResumed = true
    updateInputAllowed()
    if (settings.autoConnect) {
      wantsConnection = true
      retryDelay = 500
      ui.post(retryConnection)
    }
  }

  override fun onPause() {
    isResumed = false
    wantsConnection = false
    ui.removeCallbacksAndMessages(null)
    updateInputAllowed()
    controllerView.releaseTouches()
    stopVideo()
    client.close()
    super.onPause()
  }

  override fun onDestroy() {
    settingsDialog?.dismiss()
    ui.removeCallbacksAndMessages(null)
    settingsStore.close()
    videoClient.close()
    client.close()
    super.onDestroy()
  }
}
