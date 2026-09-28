package dev.cedarflake.ift

import androidx.test.platform.app.InstrumentationRegistry

internal object DeviceBuild {
  fun mode(): String {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    // Direct BuildConfig constants would describe the test APK due to compile-time inlining.
    val config = context.classLoader.loadClass("dev.cedarflake.ift.BuildConfig")
    return config.getField("BUILD_TYPE").get(null) as String
  }
}
