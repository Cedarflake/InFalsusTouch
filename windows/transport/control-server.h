#pragma once

#include <atomic>

#include "windows/config/host-options.h"
#include "windows/input/input-state.h"
#include "windows/target/game-window.h"

namespace ift {

void runControlServer(const HostOptions& options, const GameWindow& target,
                      InputSink& sink, const std::atomic_bool& stopping);

}
