package dev.cedarflake.ift

import android.os.ParcelFileDescriptor
import android.os.SystemClock

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference

class DeviceActivity(private val activity: MainActivity) : Closeable {
  fun onActivity(action: (MainActivity) -> Unit) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync { action(activity) }
  }

  override fun close() { onActivity { it.finish() } }

  companion object {
    fun launch(): DeviceActivity {
      val instrumentation = InstrumentationRegistry.getInstrumentation()
      val command = "am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER " +
        "-f 0x10008000 -n dev.cedarflake.infalsustouch/dev.cedarflake.ift.MainActivity"
      ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
      val current = AtomicReference<MainActivity?>()
      val deadline = SystemClock.uptimeMillis() + 5000
      while (SystemClock.uptimeMillis() < deadline) {
        instrumentation.runOnMainSync {
          current.set(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<MainActivity>().firstOrNull())
        }
        current.get()?.let {
          instrumentation.waitForIdleSync()
          // Activity resume precedes the system's landscape rotation animation.
          SystemClock.sleep(500)
          return DeviceActivity(it)
        }
        SystemClock.sleep(50)
      }
      error("Controller Activity did not resume after shell launch")
    }
  }
}
