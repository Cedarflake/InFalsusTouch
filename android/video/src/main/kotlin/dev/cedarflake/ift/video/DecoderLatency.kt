package dev.cedarflake.ift.video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import android.view.Surface

internal object DecoderLatency {
  private val vendorOptions = listOf(
    "vendor.qti-ext-dec-low-latency.enable",
    "vendor.rtc-ext-dec-low-latency.enable",
    "vendor.low-latency.enable",
  )

  fun configure(codec: MediaCodec, capabilities: MediaCodecInfo.CodecCapabilities, format: MediaFormat, surface: Surface,
    restoreAfterReset: () -> Unit = {}): String {
    val option = supportedOption(codec, capabilities)
    if (option != null) format.setInteger(option, 1)
    try {
      codec.configure(format, surface, null, 0)
    } catch (error: Exception) {
      if (option == null) throw error
      Log.w("InFalsusTouchVideo", "Decoder rejected $option; retrying without it", error)
      codec.reset()
      restoreAfterReset()
      if (Build.VERSION.SDK_INT >= 29) format.removeKey(option)
      codec.configure(format, surface, null, 0)
      return "unavailable (configuration rejected)"
    }
    return option ?: "unavailable"
  }

  private fun supportedOption(codec: MediaCodec, capabilities: MediaCodecInfo.CodecCapabilities): String? {
    if (Build.VERSION.SDK_INT >= 30 && capabilities.isFeatureSupported(MediaCodecInfo.CodecCapabilities.FEATURE_LowLatency)) {
      return MediaFormat.KEY_LOW_LATENCY
    }
    if (Build.VERSION.SDK_INT < 31) return null
    val advertised = try {
      val supported = codec.supportedVendorParameters
      vendorOptions.firstOrNull { it in supported }
    } catch (error: Exception) {
      Log.w("InFalsusTouchVideo", "Cannot query decoder latency options", error)
      null
    }
    if (advertised != null) return advertised
    // Qualcomm OMX extensions can work without being listed by the newer discovery API.
    return if (codec.name.startsWith("OMX.qcom.", ignoreCase = true)) vendorOptions.first() else null
  }
}
