#include "imgui/imgui.h"
#include "imgui/imgui_internal.h"
#include "cimgui.h"
#include "cimgui_custom.h"

CIMGUI_API bool igButtonXY(const char* label, float x, float y)
{
    return ImGui::Button(label, ImVec2(x, y));
}

CIMGUI_API void igSetNextWindowSizeXY(float x, float y, ImGuiCond cond)
{
    return ImGui::SetNextWindowSize(ImVec2(x, y), cond);
}
