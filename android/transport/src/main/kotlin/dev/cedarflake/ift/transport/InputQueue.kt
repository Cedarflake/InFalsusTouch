package dev.cedarflake.ift.transport

class OutboundEvent {
  var type = MessageType.PING
  var lane = 0
  var value = 0f
  var timestampNs = 0L
}

class InputQueue(private val capacity: Int = 256) {
  private val lock = Object()
  private val types = arrayOfNulls<MessageType>(capacity)
  private val lanes = IntArray(capacity)
  private val values = FloatArray(capacity)
  private val timestamps = LongArray(capacity)
  private var head = 0
  private var size = 0
  private var closed = false

  init { require(capacity > 0) }

  fun offer(type: MessageType, lane: Int = 0, value: Float = 0f, timestampNs: Long = System.nanoTime()): Boolean = synchronized(lock) {
    if (closed) return false
    val tail = (head + size - 1 + capacity) % capacity
    if (size > 0 && type == MessageType.FIELD_ABSOLUTE && types[tail] == type) {
      values[tail] = value
      timestamps[tail] = timestampNs
      return true
    }
    if (size == capacity) return false
    val index = (head + size) % capacity
    types[index] = type
    lanes[index] = lane
    values[index] = value
    timestamps[index] = timestampNs
    size++
    lock.notifyAll()
    true
  }

  fun takeInto(event: OutboundEvent, timeoutMs: Long): Boolean = synchronized(lock) {
    val deadline = System.nanoTime() + timeoutMs * 1_000_000
    while (size == 0 && !closed) {
      val remaining = deadline - System.nanoTime()
      if (remaining <= 0) return false
      lock.wait(remaining / 1_000_000, (remaining % 1_000_000).toInt())
    }
    if (size == 0) return false
    event.type = requireNotNull(types[head])
    event.lane = lanes[head]
    event.value = values[head]
    event.timestampNs = timestamps[head]
    types[head] = null
    head = (head + 1) % capacity
    size--
    true
  }

  fun clear() = synchronized(lock) {
    types.fill(null)
    size = 0
    head = 0
  }

  fun close() = synchronized(lock) {
    closed = true
    clear()
    lock.notifyAll()
  }
}
