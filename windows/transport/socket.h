#pragma once

#include <WinSock2.h>
#include <WS2tcpip.h>

#include <stdexcept>
#include <string>

namespace ift {

inline std::runtime_error socketError(const char* operation) {
  return std::runtime_error(std::string(operation) + ": Winsock " + std::to_string(WSAGetLastError()));
}

class Winsock {
public:
  Winsock() {
    WSADATA data{};
    const int error = WSAStartup(MAKEWORD(2, 2), &data);
    if (error != 0) {
      throw std::runtime_error("WSAStartup failed: " + std::to_string(error));
    }
  }
  ~Winsock() { WSACleanup(); }
};

class Socket {
public:
  explicit Socket(SOCKET value) : value_(value) {
    if (value == INVALID_SOCKET) {
      throw socketError("socket/accept");
    }
  }
  ~Socket() { closesocket(value_); }
  Socket(const Socket&) = delete;
  Socket& operator=(const Socket&) = delete;
  SOCKET get() const { return value_; }
  void nonblocking() const {
    u_long enabled = 1;
    if (ioctlsocket(value_, FIONBIO, &enabled) != 0) {
      throw socketError("ioctlsocket");
    }
  }

private:
  SOCKET value_;
};

}
