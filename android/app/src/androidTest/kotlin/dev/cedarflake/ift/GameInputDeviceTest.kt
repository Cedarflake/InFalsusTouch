package dev.cedarflake.ift

import android.os.SystemClock

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry

import dev.cedarflake.ift.transport.ConnectionState
import dev.cedarflake.ift.transport.ControlClient
import dev.cedarflake.ift.transport.ControlListener
import dev.cedarflake.ift.transport.MessageType

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class GameInputDeviceTest {
  @Test fun oneUsbLaneReachesTheSelectedForegroundGame() {
    val arguments = InstrumentationRegistry.getArguments()
    assumeTrue(arguments.getString("gameInput") == "true")
    val lanes = requireNotNull(arguments.getString("lane")).split(',').map { it.toInt() }
    require(lanes.isNotEmpty() && lanes.distinct().size == lanes.size && lanes.all { it in 1..6 })
    val ready = CountDownLatch(1)
    val active = AtomicBoolean()
    ControlClient(object : ControlListener {
      override fun onState(state: ConnectionState, detail: String) {
        if (state == ConnectionState.DISCONNECTED) active.set(false)
      }
      override fun onTargetReady(value: Boolean) {
        active.set(value)
        if (value) ready.countDown()
      }
      override fun onRtt(milliseconds: Double) = Unit
    }, { it.run() }).use { client ->
      client.connect()
      assertTrue("Focus the game before sending a test key", ready.await(5, TimeUnit.SECONDS))
      for (lane in lanes) client.send(MessageType.LANE_DOWN, lane)
      SystemClock.sleep(300)
      for (lane in lanes) client.send(MessageType.LANE_UP, lane)
      client.send(MessageType.RELEASE_ALL)
      SystemClock.sleep(500)
      assertTrue("Game focus or USB connection was lost", active.get())
    }
  }
}
