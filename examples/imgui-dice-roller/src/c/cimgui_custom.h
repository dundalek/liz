#include "cimgui.h"

// Workaround for struct passing like ImVec2 not being supported: https://github.com/ziglang/zig/issues/1481

CIMGUI_API bool igButtonXY(const char* label, float x, float y);
CIMGUI_API void igSetNextWindowSizeXY(float x, float y, ImGuiCond cond);
