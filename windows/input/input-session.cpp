#include "windows/input/input-session.h"

namespace ift {

InputSession::InputSession(InputState& state) : state_(state) {}

Packet InputSession::process(const Packet& packet) {
  validatePacket(packet);
  if (packet.type == MessageType::ack || packet.type == MessageType::configuration || packet.sequence != nextSequence_ ||
      (!hasHello_ && packet.type != MessageType::hello) ||
      (hasHello_ && packet.type == MessageType::hello)) {
    throw ProtocolError("Invalid session handshake/sequence/direction");
  }
  ++nextSequence_;
  hasHello_ = true;
  const bool ready = state_.apply(packet);
  return {MessageType::ack, 0, static_cast<std::uint8_t>(ready ? 0 : 1),
          packet.sequence, 0, packet.timestampNs};
}

}
