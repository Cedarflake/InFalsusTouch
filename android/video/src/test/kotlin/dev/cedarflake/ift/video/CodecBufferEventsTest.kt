package dev.cedarflake.ift.video

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

class CodecBufferEventsTest {
  @Test fun flushDiscardsOwnedBuffersAndCallbacksBeforeTheBarrier() {
    var wakes = 0
    val events = CodecBufferEvents { wakes++ }
    events.input(3)
    events.output(7, 123)
    events.suspendCallbacks()
    events.input(4)
    events.output(8, 124)
    assertNull(events.takeInput())
    assertNull(events.takeOutput())
    assertEquals(2, wakes)
    events.resumeCallbacks()
    events.input(3)
    events.output(7, 125)
    assertEquals(3, events.takeInput())
    assertEquals(CodecOutput(7, 125), events.takeOutput())
    assertEquals(4, wakes)
  }

  @Test fun failureDuringFlushWakesTheWorkerAndSurvivesResuming() {
    var wakes = 0
    val events = CodecBufferEvents { wakes++ }
    val error = IllegalStateException("codec failed")
    events.suspendCallbacks()
    events.failed(error)
    events.resumeCallbacks()
    assertEquals(1, wakes)
    assertSame(error, assertFailsWith<IllegalStateException> { events.checkFailure() })
    var accessed = false
    assertSame(error, assertFailsWith<IllegalStateException> { events.useBuffer { accessed = true } })
    assertFalse(accessed)
  }

  @Test fun callbacksAfterCloseCannotReviveTheSession() {
    var wakes = 0
    val events = CodecBufferEvents { wakes++ }
    events.close()
    events.resumeCallbacks()
    events.input(1)
    events.output(2, 3)
    events.failed(IllegalStateException("closed"))
    assertEquals(0, wakes)
    assertNull(events.takeInput())
    assertNull(events.takeOutput())
    events.checkFailure()
  }
}
