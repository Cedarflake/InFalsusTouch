#pragma once

#include <atomic>

#include "windows/config/host-options.h"
#include "windows/video/video-frame-rate.h"
#include "windows/input/input-state.h"
#include "windows/target/game-window.h"

namespace ift {

void checkControlPort(std::uint16_t port);
void runControlServer(const HostOptions& options, const GameWindow& target,
                      InputSink& sink, VideoFrameRate& frameRate, const std::atomic_bool& stopping);

}
