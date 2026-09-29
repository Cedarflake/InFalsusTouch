#pragma once

#include <Windows.h>

#include <atomic>
#include <stop_token>

#include "windows/video/video-options.h"
#include "windows/video/video-frame-rate.h"

namespace ift {

void runVideoServer(VideoOptions options, HWND window, const VideoFrameRate& frameRate, const std::atomic_bool& stopping,
                    std::stop_token token) noexcept;

}
