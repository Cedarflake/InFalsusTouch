package dev.cedarflake.ift

import androidx.test.platform.app.InstrumentationRegistry

import dev.cedarflake.ift.settings.ControlSettings

import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DeviceSettings(update: (ControlSettings) -> ControlSettings) : Closeable {
  private val store = SettingsStore(InstrumentationRegistry.getInstrumentation().targetContext)
  private val original = store.load().also { check(!it.recovered) { "Fix invalid app preferences before device tests" } }.value

  init { save(update(original)) }

  private fun save(value: ControlSettings) {
    val finished = CountDownLatch(1)
    val success = AtomicBoolean()
    store.save(value) { success.set(it); finished.countDown() }
    check(finished.await(3, TimeUnit.SECONDS) && success.get()) { "Could not persist device test settings" }
  }

  override fun close() {
    try { save(original) } finally { store.close() }
  }
}
