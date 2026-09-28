package dev.cedarflake.ift

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.RectF
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast

import dev.cedarflake.ift.settings.ControlSettings
import dev.cedarflake.ift.settings.SettingsCodec
import dev.cedarflake.ift.touch.TouchSink
import dev.cedarflake.ift.transport.ConnectionState
import dev.cedarflake.ift.transport.ControllerConfiguration
import dev.cedarflake.ift.transport.ControlClient
import dev.cedarflake.ift.transport.ControlListener
import dev.cedarflake.ift.transport.MessageType
import dev.cedarflake.ift.video.VideoClient
import dev.cedarflake.ift.video.VideoListener
import dev.cedarflake.ift.video.VideoSnapshot

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.android.FlutterView
import io.flutter.embedding.android.RenderMode
import io.flutter.embedding.android.TransparencyMode
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

import java.util.concurrent.Executor
import java.util.Locale

class MainActivity : FlutterActivity(), SurfaceHolder.Callback {
  private lateinit var root: ControllerRoot
  private lateinit var controllerView: ControllerView
  private lateinit var viewport: VideoViewport
  private lateinit var flutterUi: FlutterView
  private lateinit var channel: MethodChannel
  private lateinit var settingsStore: SettingsStore
  private lateinit var client: ControlClient
  private lateinit var videoClient: VideoClient
  private val ui = Handler(Looper.getMainLooper())
  private var settings = ControlSettings()
  private var state = ConnectionState.DISCONNECTED
  private var panel = "compact"
  private var isTargetReady = false
  private var isResumed = false
  private var isVideoStarted = false
  private var isStatePending = false
  private var wantsConnection = false
  private var retryScheduled = false
  private var connectionFailed = false
  private var retryDelay = 500L
  private var inputRtt = 0.0
  private var detail = ""
  private var videoDetail = ""
  private var pendingFeedback: Int? = null
  private var peers = 0
  private var bindingStatus = 1
  private var keyLabels = ControllerConfiguration.defaultLabels
  private var fieldStatus = 0
  private var settingsHint: Toast? = null
  private var feedbackToast: Toast? = null
  private val isConfiguring get() = panel in setOf("settings", "calibrateField", "calibrateJudgment")
  @Volatile var videoSnapshot: VideoSnapshot? = null
    private set

  private val retryConnection = Runnable {
    retryScheduled = false
    if (isResumed && wantsConnection && state == ConnectionState.DISCONNECTED) client.connect()
  }
  private val publishPending = Runnable { isStatePending = false; publishState() }

  override fun getRenderMode() = RenderMode.texture
  override fun getTransparencyMode() = TransparencyMode.transparent

  override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
    super.configureFlutterEngine(flutterEngine)
    channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "dev.cedarflake.ift/ui")
    channel.setMethodCallHandler { call, result ->
      try {
        when (call.method) {
          "state" -> result.success(uiSnapshot())
          "connect" -> { toggleConnection(); result.success(uiSnapshot()) }
          "panel" -> { setPanel(requireNotNull(call.arguments as? String)); result.success(uiSnapshot()) }
          "settingsHint" -> {
            val message = requireNotNull(call.arguments as? String)
            require(message.isNotBlank() && message.length <= 120)
            if (panel == "compact" && isResumed) {
              feedbackToast?.cancel()
              settingsHint?.cancel()
              settingsHint = Toast.makeText(this, message, Toast.LENGTH_SHORT).also { it.show() }
            }
            result.success(null)
          }
          "feedback" -> {
            showFeedback(requireNotNull(call.arguments as? String))
            result.success(null)
          }
          "uiRegions" -> {
            val data = requireNotNull(call.arguments as? Map<*, *>)
            if (data["panel"] == panel) {
              val density = resources.displayMetrics.density
              val regions = requireNotNull(data["rects"] as? List<*>)
              require(regions.size <= 4)
              root.interfaceRegions = regions.map { entry ->
                val values = requireNotNull(entry as? List<*>)
                require(values.size == 4)
                val coordinates = values.map { requireNotNull(it as? Number).toFloat().also { value -> require(value.isFinite()) } * density }
                RectF(coordinates[0], coordinates[1], coordinates[2], coordinates[3])
              }
            }
            result.success(null)
          }
          "save" -> {
            val next = SettingsCodec.apply(settings, requireNotNull(call.arguments as? Map<*, *>))
            saveSettings(next) { success ->
              if (success) result.success(uiSnapshot()) else result.error("settings_save_failed", "Settings could not be saved", null)
            }
          }
          "defaults" -> saveSettings(ControlSettings(language = settings.language, theme = settings.theme)) { success ->
            if (success) result.success(uiSnapshot()) else result.error("settings_save_failed", "Settings could not be saved", null)
          }
          else -> result.notImplemented()
        }
      } catch (_: IllegalArgumentException) {
        result.error("invalid_settings", "Invalid controller settings", null)
      }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    flutterUi = findViewById(FLUTTER_VIEW_ID)
    (flutterUi.parent as ViewGroup).removeView(flutterUi)
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    if (Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply {
      layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
    settingsStore = SettingsStore(this)
    val loaded = settingsStore.load()
    settings = loaded.value
    if (loaded.recovered) pendingFeedback = R.string.settings_recovered
    client = ControlClient(object : ControlListener {
      override fun onState(state: ConnectionState, detail: String) {
        if (isDestroyed) return
        this@MainActivity.state = state
        this@MainActivity.detail = detail
        if (state == ConnectionState.DISCONNECTED) {
          peers = 0
          fieldStatus = 0
          controllerView.setFieldBusy(false)
          setTargetReady(false)
          stopVideo()
          if (isResumed && wantsConnection && settings.autoConnect) {
            retryScheduled = true
            connectionFailed = true
            ui.removeCallbacks(retryConnection)
            ui.postDelayed(retryConnection, retryDelay)
            retryDelay = (retryDelay * 2).coerceAtMost(4000)
          } else {
            connectionFailed = wantsConnection
            wantsConnection = false
            retryScheduled = false
          }
        } else if (state == ConnectionState.CONNECTED) {
          retryScheduled = false
          connectionFailed = false
          retryDelay = 500
          startVideo()
        }
        publishState()
      }
      override fun onTargetReady(ready: Boolean) { setTargetReady(ready) }
      override fun onRtt(milliseconds: Double) { inputRtt = milliseconds; queueState() }
      override fun onConfiguration(configuration: ControllerConfiguration) {
        peers = configuration.peers
        bindingStatus = configuration.bindingStatus
        keyLabels = configuration.labels
        controllerView.setBindings(keyLabels)
        queueState()
      }
      override fun onFieldStatus(status: Int) {
        fieldStatus = status
        controllerView.setFieldBusy(status == 2)
        queueState()
      }
    }, Executor { runOnUiThread(it) })
    videoClient = VideoClient(object : VideoListener {
      override fun onStatus(detail: String) {
        videoDetail = detail
        videoSnapshot = null
        controllerView.setVideoVisible(false)
        queueState()
      }
      override fun onFormat(width: Int, height: Int) { viewport.setFormat(width, height) }
      override fun onStatistics(snapshot: VideoSnapshot) {
        videoSnapshot = snapshot
        controllerView.setVideoVisible(snapshot.presentedFrames > 0)
        queueState()
      }
    }, Executor { runOnUiThread(it) })
    createViews()
    applySettings(settings)
    enterImmersiveMode()
  }

  private fun createViews() {
    root = ControllerRoot(this)
    controllerView = ControllerView(this, object : TouchSink {
      override fun laneDown(lane: Int) { client.send(MessageType.LANE_DOWN, lane) }
      override fun laneUp(lane: Int) { client.send(MessageType.LANE_UP, lane) }
      override fun fieldAbsolute(x: Float) { client.send(MessageType.FIELD_ABSOLUTE, value = x) }
      override fun fieldRelative(deltaX: Float) { client.send(MessageType.FIELD_RELATIVE, value = deltaX) }
      override fun fieldBegin() { client.send(MessageType.FIELD_BEGIN) }
      override fun fieldEnd() { client.send(MessageType.FIELD_END) }
      override fun releaseAll() { client.send(MessageType.RELEASE_ALL) }
    }).apply { tag = "controller" }
    viewport = VideoViewport(this)
    viewport.onPlacement = { placement -> controllerView.setVideoPlacement(placement); queueState() }
    viewport.surface.holder.addCallback(this)
    root.addView(viewport, FrameLayout.LayoutParams(-1, -1))
    root.addView(controllerView, FrameLayout.LayoutParams(-1, -1))
    root.addView(flutterUi, FrameLayout.LayoutParams(-1, -1))
    root.gameplay = controllerView
    root.interfaceView = flutterUi
    setContentView(root)
  }

  fun toggleConnection() {
    controllerView.releaseTouches()
    ui.removeCallbacks(retryConnection)
    retryScheduled = false
    if (state == ConnectionState.DISCONNECTED && !wantsConnection) {
      wantsConnection = true
      connectionFailed = false
      retryDelay = 500
      client.connect()
    } else {
      wantsConnection = false
      client.close()
    }
    publishState()
  }

  fun setPanel(value: String) {
    require(value in setOf("toolbar", "compact", "settings", "calibrateField", "calibrateJudgment"))
    settingsHint?.cancel()
    settingsHint = null
    if (value == "calibrateJudgment" && (videoSnapshot?.presentedFrames ?: 0L) == 0L) {
      showFeedback("video_required")
      return
    }
    panel = if (value == "toolbar") "compact" else value
    root.isConfiguring = isConfiguring
    updateInputAllowed()
    publishState()
  }

  private fun showFeedback(code: String) {
    val message = when (code) {
      "video_required" -> R.string.video_required
      "settings_recovered" -> R.string.settings_recovered
      "settings_save_failed" -> R.string.settings_save_failed
      "invalid_settings" -> R.string.invalid_settings
      "invalid_calibration" -> R.string.invalid_calibration
      "bridge_unavailable" -> R.string.bridge_unavailable
      else -> R.string.action_failed
    }
    showFeedback(message)
  }

  private fun showFeedback(message: Int) {
    if (!isResumed) {
      pendingFeedback = message
      return
    }
    val localized = if (settings.language == "system") this else createConfigurationContext(
      Configuration(resources.configuration).apply { setLocale(Locale.forLanguageTag(settings.language)) },
    )
    settingsHint?.cancel()
    settingsHint = null
    feedbackToast?.cancel()
    feedbackToast = Toast.makeText(this, localized.getText(message), Toast.LENGTH_SHORT).also { it.show() }
  }

  private fun setTargetReady(value: Boolean) {
    isTargetReady = value
    updateInputAllowed()
    queueState()
  }

  private fun updateInputAllowed() {
    if (::controllerView.isInitialized) controllerView.setInputAllowed(isTargetReady && hasWindowFocus() && isResumed && !isConfiguring)
  }

  private fun applySettings(value: ControlSettings) {
    settings = value
    controllerView.setSettings(value)
    client.setControls(value.controlsMask)
    viewport.setSettings(value)
    window.attributes = window.attributes.apply { preferredRefreshRate = if (value.highRefreshDisplay) 120f else 0f }
    queueState()
  }

  private fun saveSettings(value: ControlSettings, completed: (Boolean) -> Unit) {
    val enableAutoConnect = !settings.autoConnect && value.autoConnect
    applySettings(value)
    settingsStore.save(value) { success ->
      if (!isDestroyed) {
        publishState()
        completed(success)
      }
    }
    if (!value.autoConnect) {
      ui.removeCallbacks(retryConnection)
      retryScheduled = false
      if (state == ConnectionState.DISCONNECTED) wantsConnection = false
    }
    if (enableAutoConnect && isResumed && state == ConnectionState.DISCONNECTED) {
      wantsConnection = true
      ui.post(retryConnection)
    }
  }

  fun uiSnapshot(): Map<String, Any?> {
    val locale = if (settings.language == "system") resources.configuration.locales[0].language else settings.language
    return mapOf(
      "panel" to panel, "connection" to state.name, "targetReady" to isTargetReady,
      "searching" to (state == ConnectionState.CONNECTING || retryScheduled), "connectionFailed" to connectionFailed,
      "settings" to SettingsCodec.encode(settings), "language" to if (locale == "zh") "zh" else "en",
      "density" to resources.displayMetrics.density.toDouble(), "rtt" to inputRtt,
      "cutouts" to if (Build.VERSION.SDK_INT >= 28 && ::root.isInitialized) root.rootWindowInsets?.displayCutout?.boundingRects?.map {
        listOf(it.left, it.top, it.right, it.bottom)
      } else emptyList<List<Int>>(),
      "displayHz" to if (::root.isInitialized) (root.display?.mode?.refreshRate ?: 0f).toDouble() else 0.0,
      "hasVideo" to ((videoSnapshot?.presentedFrames ?: 0) > 0), "detail" to detail, "videoDetail" to videoDetail,
      "peers" to peers, "bindingStatus" to bindingStatus, "keyLabels" to keyLabels, "fieldStatus" to fieldStatus,
      "picture" to if (::viewport.isInitialized) viewport.placement?.let { mapOf(
        "left" to it.left, "top" to it.top, "width" to it.width, "height" to it.height, "clipHeight" to it.clipHeight,
      ) } else null,
      "video" to videoSnapshot?.let { mapOf(
        "receiveFps" to it.receiveFps, "presentFps" to it.presentFps, "decoderMs" to it.decoderMs,
        "receiveToPresentMs" to it.receiveToPresentMs, "captureToEncodeMs" to it.captureToEncodeMs,
        "megabitsPerSecond" to it.megabitsPerSecond, "queueDepth" to it.queueDepth, "droppedFrames" to it.droppedFrames,
      ) },
    )
  }

  private fun queueState() {
    if (isStatePending || isDestroyed) return
    isStatePending = true
    ui.postDelayed(publishPending, 250)
  }

  private fun publishState() {
    if (!isDestroyed && ::channel.isInitialized && ::root.isInitialized) channel.invokeMethod("state", uiSnapshot())
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
    queueState()
  }

  override fun surfaceCreated(holder: SurfaceHolder) { startVideo() }
  override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
  override fun surfaceDestroyed(holder: SurfaceHolder) { stopVideo() }

  @Suppress("DEPRECATION")
  private fun enterImmersiveMode() {
    if (Build.VERSION.SDK_INT >= 30) {
      window.setDecorFitsSystemWindows(false)
      window.insetsController?.apply {
        hide(WindowInsets.Type.systemBars())
        systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
      }
    } else window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
      View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
      View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    updateInputAllowed()
    if (hasFocus) enterImmersiveMode()
  }

  override fun onResume() {
    super.onResume()
    isResumed = true
    pendingFeedback?.let {
      pendingFeedback = null
      showFeedback(it)
    }
    updateInputAllowed()
    if (settings.autoConnect) {
      wantsConnection = true
      retryDelay = 500
      ui.post(retryConnection)
    }
  }

  override fun onPause() {
    settingsHint?.cancel()
    settingsHint = null
    feedbackToast?.cancel()
    feedbackToast = null
    isResumed = false
    wantsConnection = false
    retryScheduled = false
    ui.removeCallbacks(retryConnection)
    updateInputAllowed()
    controllerView.releaseTouches()
    stopVideo()
    client.close()
    super.onPause()
  }

  override fun onDestroy() {
    channel.setMethodCallHandler(null)
    ui.removeCallbacksAndMessages(null)
    settingsStore.close()
    videoClient.close()
    client.close()
    super.onDestroy()
  }
}
