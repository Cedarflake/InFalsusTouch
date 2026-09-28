package dev.cedarflake.ift

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.cedarflake.ift.transport.ConnectionState
import dev.cedarflake.ift.transport.ControlClient
import dev.cedarflake.ift.transport.ControlListener
import dev.cedarflake.ift.transport.MessageType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UsbTransportDeviceTest {
  @Test fun phoneReachesWindowsHostThroughUsbAndReleasesOnDisconnect() {
    assumeTrue(InstrumentationRegistry.getArguments().getString("usbHost") == "true")
    val ready = CountDownLatch(1)
    val roundTrips = CountDownLatch(3)
    val disconnected = CountDownLatch(1)
    val listener = object : ControlListener {
      override fun onState(state: ConnectionState, detail: String) {
        if (state == ConnectionState.DISCONNECTED) disconnected.countDown()
      }
      override fun onTargetReady(isReady: Boolean) { if (isReady) ready.countDown() }
      override fun onRtt(milliseconds: Double) { if (milliseconds >= 0) roundTrips.countDown() }
    }
    ControlClient(listener, Executor { it.run() }).use { client ->
      client.connect()
      assertTrue("USB host never became ready", ready.await(5, TimeUnit.SECONDS))
      for (lane in 1..6) client.send(MessageType.LANE_DOWN, lane)
      client.send(MessageType.FIELD_ABSOLUTE, value = 0.5f)
      assertTrue("USB ACKs stopped", roundTrips.await(5, TimeUnit.SECONDS))
      client.close()
      assertTrue(disconnected.await(2, TimeUnit.SECONDS))
    }
  }
}
