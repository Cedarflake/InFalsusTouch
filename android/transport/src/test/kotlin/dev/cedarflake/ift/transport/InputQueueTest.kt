package dev.cedarflake.ift.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputQueueTest {
  @Test fun fieldCoalescingNeverCrossesKeyTransitions() {
    val queue = InputQueue(4)
    queue.offer(MessageType.FIELD_ABSOLUTE, value = 0.1f)
    queue.offer(MessageType.FIELD_ABSOLUTE, value = 0.2f)
    queue.offer(MessageType.LANE_DOWN, lane = 3)
    queue.offer(MessageType.FIELD_ABSOLUTE, value = 0.8f)
    queue.offer(MessageType.LANE_UP, lane = 3)
    val event = OutboundEvent()
    assertTrue(queue.takeInto(event, 0))
    assertEquals(0.2f, event.value)
    for (type in listOf(MessageType.LANE_DOWN, MessageType.FIELD_ABSOLUTE, MessageType.LANE_UP)) {
      assertTrue(queue.takeInto(event, 0))
      assertEquals(type, event.type)
    }
    assertFalse(queue.takeInto(event, 0))
  }

  @Test fun overflowIsExplicitAndClearRemovesStaleHolds() {
    val queue = InputQueue(2)
    assertTrue(queue.offer(MessageType.LANE_DOWN, 1))
    assertTrue(queue.offer(MessageType.LANE_UP, 1))
    assertFalse(queue.offer(MessageType.LANE_DOWN, 2))
    queue.clear()
    assertTrue(queue.offer(MessageType.RELEASE_ALL))
    val event = OutboundEvent()
    assertTrue(queue.takeInto(event, 0))
    assertEquals(MessageType.RELEASE_ALL, event.type)
    queue.close()
    assertFalse(queue.offer(MessageType.LANE_DOWN, 1))
    assertFalse(queue.takeInto(event, 100))
  }

  @Test fun relativeDeltasRemainOrdered() {
    val queue = InputQueue()
    queue.offer(MessageType.FIELD_RELATIVE, value = 0.1f)
    queue.offer(MessageType.FIELD_RELATIVE, value = -0.2f)
    val event = OutboundEvent()
    queue.takeInto(event, 0)
    assertEquals(0.1f, event.value)
    queue.takeInto(event, 0)
    assertEquals(-0.2f, event.value)
  }
}
