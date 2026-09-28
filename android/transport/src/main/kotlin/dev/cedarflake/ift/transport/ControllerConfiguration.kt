package dev.cedarflake.ift.transport

data class ControllerConfiguration(val controls: Int, val peers: Int, val bindingStatus: Int, val keys: List<Int>) {
  val labels: List<String> get() = keys.map(::keyLabel)

  companion object {
    val defaultLabels = listOf("Shift", "A", "S", "D", "F", "Space")

    fun decode(packet: Packet): ControllerConfiguration {
      require(packet.type == MessageType.CONFIGURATION)
      return ControllerConfiguration(packet.lane, packet.value.toInt(), packet.status,
        List(6) { (packet.timestampNs ushr ((5 - it) * 8) and 255).toInt() })
    }

    fun keyLabel(key: Int): String = when (key) {
      in 15..40 -> ('A'.code + key - 15).toChar().toString()
      in 41..49 -> (key - 40).toString()
      50 -> "0"
      in 84..93 -> "Num ${key - 84}"
      in 94..105 -> "F${key - 93}"
      in 1..14 -> listOf("Space", "Enter", "Tab", "`", "'", ";", ",", ".", "/", "\\", "[", "]", "-", "=")[key - 1]
      in 51..83 -> listOf("Shift", "R Shift", "Alt", "R Alt", "Ctrl", "R Ctrl", "Win", "R Win", "Menu", "Esc",
        "←", "→", "↑", "↓", "Backspace", "PgDn", "PgUp", "Home", "End", "Insert", "Delete", "Caps", "NumLock",
        "PrtSc", "Scroll", "Pause", "Num Enter", "Num /", "Num *", "Num +", "Num -", "Num .", "Num =")[key - 51]
      else -> "?"
    }
  }
}
