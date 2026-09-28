#include "windows/transport/socket.h"
#include "windows/transport/control-server.h"

#include <chrono>
#include <deque>
#include <iostream>

#include "windows/input/input-session.h"

namespace ift {
namespace {

using Clock = std::chrono::steady_clock;
using namespace std::chrono_literals;

struct PendingWrite {
  PacketBytes bytes;
  std::size_t offset = 0;
};

void refreshTarget(InputState& state, const HostOptions& options, const GameWindow& target) {
  Rect client{0, 0, 1280, 720};
  const bool active = options.dryRun || target.activeClient(client);
  state.updateTarget(active, client);
}

void flushReplies(SOCKET socket, std::deque<PendingWrite>& replies) {
  while (!replies.empty()) {
    auto& reply = replies.front();
    const int written = send(socket, reinterpret_cast<const char*>(reply.bytes.data() + reply.offset),
                             static_cast<int>(packetSize - reply.offset), 0);
    if (written == SOCKET_ERROR && WSAGetLastError() == WSAEWOULDBLOCK) {
      return;
    }
    if (written <= 0) {
      throw socketError("send ACK");
    }
    reply.offset += static_cast<std::size_t>(written);
    if (reply.offset == packetSize) {
      replies.pop_front();
    }
  }
}

void runClient(SOCKET socket, const HostOptions& options, const GameWindow& target,
               InputState& state, const std::atomic_bool& stopping) {
  InputSession session(state);
  PacketBytes incoming{};
  std::size_t received = 0;
  std::deque<PendingWrite> replies;
  auto lastPacket = Clock::now();
  auto partialStart = lastPacket;
  auto lastMovement = lastPacket;
  while (!stopping.load()) {
    refreshTarget(state, options, target);
    const auto now = Clock::now();
    if (now - lastPacket > 500ms || (received > 0 && now - partialStart > 500ms)) {
      throw std::runtime_error("Input heartbeat/partial-packet timeout");
    }
    fd_set readable;
    fd_set writable;
    FD_ZERO(&readable);
    FD_ZERO(&writable);
    FD_SET(socket, &readable);
    if (!replies.empty()) {
      FD_SET(socket, &writable);
    }
    timeval timeout{0, 8000};
    if (select(0, &readable, &writable, nullptr, &timeout) == SOCKET_ERROR) {
      throw socketError("select client");
    }
    if (FD_ISSET(socket, &writable)) {
      flushReplies(socket, replies);
    }
    if (!FD_ISSET(socket, &readable)) {
      continue;
    }
    const int count = recv(socket, reinterpret_cast<char*>(incoming.data() + received),
                           static_cast<int>(packetSize - received), 0);
    if (count == 0) {
      return;
    }
    if (count == SOCKET_ERROR) {
      if (WSAGetLastError() == WSAEWOULDBLOCK) {
        continue;
      }
      throw socketError("recv input");
    }
    if (received == 0) {
      partialStart = Clock::now();
    }
    received += static_cast<std::size_t>(count);
    if (received != packetSize) {
      continue;
    }
    refreshTarget(state, options, target);
    const auto packet = decodePacket(incoming);
    const auto packetTime = Clock::now();
    const double seconds = std::chrono::duration<double>(packetTime - lastMovement).count();
    if (packet.type == MessageType::fieldRelative) {
      lastMovement = packetTime;
    }
    const auto reply = session.process(packet, seconds);
    if (replies.size() >= 256) {
      throw std::runtime_error("Client is not consuming ACKs");
    }
    replies.push_back({encodePacket(reply), 0});
    flushReplies(socket, replies);
    received = 0;
    lastPacket = packetTime;
  }
}

}

void runControlServer(const HostOptions& options, const GameWindow& target,
                      InputSink& sink, const std::atomic_bool& stopping) {
  Winsock winsock;
  Socket listener(socket(AF_INET, SOCK_STREAM, IPPROTO_TCP));
  const BOOL exclusive = TRUE;
  if (setsockopt(listener.get(), SOL_SOCKET, SO_EXCLUSIVEADDRUSE,
                 reinterpret_cast<const char*>(&exclusive), sizeof(exclusive)) != 0) {
    throw socketError("SO_EXCLUSIVEADDRUSE");
  }
  sockaddr_in address{};
  address.sin_family = AF_INET;
  address.sin_port = htons(options.port);
  address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
  if (bind(listener.get(), reinterpret_cast<sockaddr*>(&address), sizeof(address)) != 0 ||
      listen(listener.get(), 1) != 0) {
    throw socketError("bind/listen; is another host using the port?");
  }
  listener.nonblocking();
  InputState state(sink, options.field);
  std::cout << "Listening on 127.0.0.1:" << options.port
            << (options.dryRun ? " (dry-run)" : "") << std::endl;
  while (!stopping.load()) {
    fd_set readable;
    FD_ZERO(&readable);
    FD_SET(listener.get(), &readable);
    timeval timeout{0, 10000};
    const int ready = select(0, &readable, nullptr, nullptr, &timeout);
    if (ready == SOCKET_ERROR) {
      throw socketError("select listener");
    }
    if (ready == 0) {
      continue;
    }
    Socket client(accept(listener.get(), nullptr, nullptr));
    client.nonblocking();
    const BOOL noDelay = TRUE;
    if (setsockopt(client.get(), IPPROTO_TCP, TCP_NODELAY,
                   reinterpret_cast<const char*>(&noDelay), sizeof(noDelay)) != 0) {
      throw socketError("TCP_NODELAY");
    }
    state.updateTarget(false, {});
    std::cout << "Controller connected" << std::endl;
    try {
      runClient(client.get(), options, target, state, stopping);
    } catch (const std::exception& error) {
      // Cleanup precedes diagnostics so a slow console cannot prolong a hold.
      if (!state.releaseAll()) {
        throw std::runtime_error("Key release failed; host stopped to prevent further injection");
      }
      std::cerr << "Controller disconnected: " << error.what() << std::endl;
    }
    if (!state.releaseAll()) {
      throw std::runtime_error("Key release failed on disconnect");
    }
  }
}

}
