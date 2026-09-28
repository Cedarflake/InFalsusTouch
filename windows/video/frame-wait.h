#pragma once

#include <Windows.h>

#include <stdexcept>

namespace ift {

class FrameWait {
public:
  FrameWait() : timer_(CreateWaitableTimerExW(nullptr, nullptr, CREATE_WAITABLE_TIMER_HIGH_RESOLUTION, TIMER_ALL_ACCESS)) {
    if (!timer_) throw std::runtime_error("Cannot create high-resolution video timer");
  }
  ~FrameWait() { CloseHandle(timer_); }
  FrameWait(const FrameWait&) = delete;
  FrameWait& operator=(const FrameWait&) = delete;
  void wait() const {
    LARGE_INTEGER due{};
    due.QuadPart = -10'000;
    if (!SetWaitableTimerEx(timer_, &due, 0, nullptr, nullptr, nullptr, 0) ||
        WaitForSingleObject(timer_, 100) != WAIT_OBJECT_0) {
      throw std::runtime_error("Video scheduling timer failed");
    }
  }
private:
  HANDLE timer_;
};

}
