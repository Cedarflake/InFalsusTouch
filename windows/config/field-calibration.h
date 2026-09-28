#pragma once

#include <Windows.h>

#include "windows/input/field-mapper.h"

namespace ift {

FieldConfig calibrateGameWindow(HWND window, const FieldConfig& base);

}
