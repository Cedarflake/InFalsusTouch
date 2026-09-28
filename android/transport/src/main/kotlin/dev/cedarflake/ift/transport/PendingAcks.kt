package dev.cedarflake.ift.transport

internal class PendingAcks(private val capacity: Int = 1024) {
  private val sequences = IntArray(capacity)
  private val timestamps = LongArray(capacity)
  private var head = 0
  private var size = 0

  @Synchronized fun register(sequence: Int, timestamp: Long) {
    if (size == capacity) throw ProtocolException("Too many unacknowledged inputs")
    val index = (head + size) % capacity
    sequences[index] = sequence
    timestamps[index] = timestamp
    size++
  }

  @Synchronized fun acknowledge(packet: Packet) {
    if (packet.type != MessageType.ACK || size == 0 || sequences[head] != packet.sequence ||
      timestamps[head] != packet.timestampNs) {
      throw ProtocolException("Unexpected ACK sequence/timestamp")
    }
    head = (head + 1) % capacity
    size--
  }
}
