#include "windows/transport/socket.h"
#include "windows/transport/control-server.h"

#include <chrono>
#include <deque>
#include <iostream>
#include <memory>

#include "windows/config/game-bindings.h"
#include "windows/input/input-mixer.h"
#include "windows/input/input-session.h"

namespace ift {
namespace {

using Clock = std::chrono::steady_clock;
using namespace std::chrono_literals;

void bindControlPort(const Socket& listener, std::uint16_t port) {
  const BOOL exclusive = TRUE;
  if (setsockopt(listener.get(), SOL_SOCKET, SO_EXCLUSIVEADDRUSE,
      reinterpret_cast<const char*>(&exclusive), sizeof(exclusive)) != 0) throw socketError("SO_EXCLUSIVEADDRUSE");
  sockaddr_in address{};
  address.sin_family = AF_INET;
  address.sin_port = htons(port);
  address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
  if (bind(listener.get(), reinterpret_cast<sockaddr*>(&address), sizeof(address)) == 0) return;
  const auto error = WSAGetLastError();
  if (error == WSAEADDRINUSE || error == WSAEACCES) {
    throw std::runtime_error("Control port " + std::to_string(port) +
      " is unavailable. An InFalsusTouchHost may already be running; do not start a second copy.");
  }
  throw socketError("bind control listener");
}

struct PendingWrite {
  PacketBytes bytes;
  std::size_t offset = 0;
};

struct Controller {
  Controller(SOCKET accepted, InputMixer& mixer, std::size_t slot, FieldConfig field)
    : socket(accepted), input(mixer, slot), state(input, field), session(state) {
    socket.nonblocking();
    const BOOL noDelay = TRUE;
    if (setsockopt(socket.get(), IPPROTO_TCP, TCP_NODELAY,
        reinterpret_cast<const char*>(&noDelay), sizeof(noDelay)) != 0) throw socketError("TCP_NODELAY");
  }
  Socket socket;
  ControllerInput input;
  InputState state;
  InputSession session;
  PacketBytes incoming{};
  std::deque<PendingWrite> replies;
  std::size_t received = 0;
  bool joined = false;
  std::string failure;
  std::uint8_t controls = allControls;
  Clock::time_point lastPacket = Clock::now();
  Clock::time_point partialStart = lastPacket;

  void queue(const Packet& packet) {
    if (replies.size() >= 256) throw std::runtime_error("Client is not consuming replies");
    replies.push_back({encodePacket(packet), 0});
  }
  void flush() {
    while (!replies.empty()) {
      auto& reply = replies.front();
      const int written = send(socket.get(), reinterpret_cast<const char*>(reply.bytes.data() + reply.offset),
        static_cast<int>(packetSize - reply.offset), 0);
      if (written == SOCKET_ERROR && WSAGetLastError() == WSAEWOULDBLOCK) return;
      if (written <= 0) throw socketError("send reply");
      reply.offset += static_cast<std::size_t>(written);
      if (reply.offset == packetSize) replies.pop_front();
    }
  }
};

}

void checkControlPort(std::uint16_t port) {
  Winsock winsock;
  Socket listener(socket(AF_INET, SOCK_STREAM, IPPROTO_TCP));
  bindControlPort(listener, port);
}

void runControlServer(const HostOptions& options, const GameWindow& target,
                      InputSink& sink, VideoFrameRate& frameRate, const std::atomic_bool& stopping) {
  Winsock winsock;
  Socket listener(socket(AF_INET, SOCK_STREAM, IPPROTO_TCP));
  bindControlPort(listener, options.port);
  if (listen(listener.get(), static_cast<int>(maxControllers)) != 0) throw socketError("listen controllers");
  listener.nonblocking();
  GameBindings bindings(!options.syncBindings ? std::filesystem::path{} :
    options.bindingsPath.empty() ? defaultGamePreferences() : std::filesystem::path(options.bindingsPath));
  bindings.refresh();
  sink.setBindings(bindings.keys());
  InputMixer mixer(sink);
  ControlGroup group;
  std::array<std::unique_ptr<Controller>, maxControllers> controllers;
  std::uint32_t generation = 0;
  auto nextBindings = Clock::now();
  const auto publish = [&](bool resetBindings) {
    if (++generation == 0) ++generation;
    for (std::size_t index = 0; index < controllers.size(); ++index) {
      auto* controller = controllers[index].get();
      if (!controller || !controller->joined) continue;
      const auto mask = group.controls(index);
      if (resetBindings || mask != controller->controls) controller->state.setControls(mask);
      controller->controls = mask;
      try {
        controller->queue({MessageType::configuration, mask, bindings.status(), generation,
          static_cast<float>(group.size()), packBindings(bindings.keys())});
      } catch (const std::exception& error) {
        controller->failure = error.what();
      }
    }
  };
  std::cout << "Listening on 127.0.0.1:" << options.port << " (up to " << maxControllers << " controllers)"
    << (options.dryRun ? " (dry-run)" : "") << std::endl;
  while (!stopping.load()) {
    const auto now = Clock::now();
    if (now >= nextBindings) {
      if (bindings.refresh()) {
        for (auto& controller : controllers) if (controller) controller->state.setControls(controller->controls);
        sink.setBindings(bindings.keys());
        publish(false);
      }
      nextBindings = now + 500ms;
    }
    Rect client{0, 0, 1280, 720};
    const bool active = bindings.status() != 2 && (options.dryRun || target.activeClient(client));
    fd_set readable, writable;
    FD_ZERO(&readable);
    FD_ZERO(&writable);
    FD_SET(listener.get(), &readable);
    for (auto& controller : controllers) {
      if (!controller) continue;
      controller->state.updateTarget(active, client);
      FD_SET(controller->socket.get(), &readable);
      if (!controller->replies.empty()) FD_SET(controller->socket.get(), &writable);
    }
    sink.tick();
    timeval timeout{0, sink.pendingField() ? 2000L : 8000L};
    if (select(0, &readable, &writable, nullptr, &timeout) == SOCKET_ERROR) throw socketError("select controllers");
    for (std::size_t index = 0; index < controllers.size(); ++index) {
      auto* controller = controllers[index].get();
      if (!controller) continue;
      std::string failure;
      try {
        if (!controller->failure.empty()) throw std::runtime_error(controller->failure);
        const auto tick = Clock::now();
        if (tick - controller->lastPacket > 500ms ||
            (controller->received && tick - controller->partialStart > 500ms)) throw std::runtime_error("Input heartbeat/partial-packet timeout");
        if (FD_ISSET(controller->socket.get(), &writable)) controller->flush();
        if (!FD_ISSET(controller->socket.get(), &readable)) continue;
        const int count = recv(controller->socket.get(),
          reinterpret_cast<char*>(controller->incoming.data() + controller->received),
          static_cast<int>(packetSize - controller->received), 0);
        if (count == 0) throw std::runtime_error("Peer closed");
        if (count == SOCKET_ERROR) {
          if (WSAGetLastError() == WSAEWOULDBLOCK) continue;
          throw socketError("recv input");
        }
        if (!controller->received) controller->partialStart = tick;
        controller->received += static_cast<std::size_t>(count);
        if (controller->received != packetSize) continue;
        const auto packet = decodePacket(controller->incoming);
        auto reply = controller->session.process(packet);
        if (packet.type == MessageType::videoFrameRate || (packet.type == MessageType::hello && packet.value != 0)) {
          frameRate.request(index, static_cast<std::uint16_t>(packet.value));
        }
        if (!controller->joined) {
          group.join(index);
          controller->joined = true;
          publish(false);
        }
        if (packet.type == MessageType::assignControls) {
          group.assign(index, static_cast<std::uint8_t>(packet.value));
          publish(false);
        }
        reply.status = controller->state.isReady() ? controller->input.fieldStatus() : 1;
        controller->queue(reply);
        controller->flush();
        controller->received = 0;
        controller->lastPacket = tick;
      } catch (const std::exception& error) {
        failure = error.what();
      }
      if (!failure.empty()) {
        if (!controller->state.releaseAll()) throw std::runtime_error("Key release failed; host stopped to prevent further injection");
        if (controller->joined) group.leave(index);
        frameRate.clear(index);
        controllers[index].reset();
        publish(false);
        std::cerr << "Controller " << index + 1 << " disconnected: " << failure << std::endl;
      }
    }
    if (FD_ISSET(listener.get(), &readable)) {
      const SOCKET accepted = accept(listener.get(), nullptr, nullptr);
      if (accepted == INVALID_SOCKET) {
        if (WSAGetLastError() == WSAEWOULDBLOCK) continue;
        throw socketError("accept controller");
      }
      std::size_t slot = 0;
      while (slot < controllers.size() && controllers[slot]) ++slot;
      if (slot == controllers.size()) { closesocket(accepted); continue; }
      controllers[slot] = std::make_unique<Controller>(accepted, mixer, slot, options.field);
      std::cout << "Controller " << slot + 1 << " connected" << std::endl;
    }
  }
}

}
