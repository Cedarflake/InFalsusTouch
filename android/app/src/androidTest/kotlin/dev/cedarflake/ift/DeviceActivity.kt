package dev.cedarflake.ift

import android.os.ParcelFileDescriptor
import android.os.SystemClock

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference

class DeviceActivity(private var activity: MainActivity) : Closeable {
  fun onActivity(action: (MainActivity) -> Unit) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync { action(activity) }
  }

  override fun close() { onActivity { it.finish() } }

  fun recreate() {
    val previous = activity
    onActivity { it.recreate() }
    activity = waitForResumed { it !== previous }
    waitForStage(previous, Stage.DESTROYED)
  }

  fun background() {
    onActivity { check(it.moveTaskToBack(true)) { "Controller task did not move to the background" } }
    waitForStage(activity, Stage.STOPPED)
  }

  fun resume() {
    start(clearTask = false)
    waitForResumed { it === activity }
  }

  companion object {
    fun launch(): DeviceActivity {
      start(clearTask = true)
      return DeviceActivity(waitForResumed { true })
    }

    private fun start(clearTask: Boolean) {
      val instrumentation = InstrumentationRegistry.getInstrumentation()
      val flags = if (clearTask) "0x10008000" else "0x10000000"
      val command = "am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER " +
        "-f $flags -n dev.cedarflake.infalsustouch/dev.cedarflake.ift.MainActivity"
      ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes() }
    }

    private fun waitForResumed(matches: (MainActivity) -> Boolean): MainActivity {
      val instrumentation = InstrumentationRegistry.getInstrumentation()
      val current = AtomicReference<MainActivity?>()
      val deadline = SystemClock.uptimeMillis() + 5000
      while (SystemClock.uptimeMillis() < deadline) {
        instrumentation.runOnMainSync {
          current.set(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<MainActivity>().firstOrNull(matches))
        }
        current.get()?.let {
          instrumentation.waitForIdleSync()
          // Activity resume precedes the system's landscape rotation animation.
          SystemClock.sleep(500)
          return it
        }
        SystemClock.sleep(50)
      }
      error("Controller Activity did not resume after shell launch")
    }

    private fun waitForStage(activity: MainActivity, expected: Stage) {
      val instrumentation = InstrumentationRegistry.getInstrumentation()
      val deadline = SystemClock.uptimeMillis() + 5000
      var stage: Stage? = null
      while (SystemClock.uptimeMillis() < deadline) {
        instrumentation.runOnMainSync {
          stage = ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(activity)
        }
        if (stage == expected) return
        SystemClock.sleep(50)
      }
      error("Controller Activity did not reach $expected; last stage=$stage")
    }
  }
}
