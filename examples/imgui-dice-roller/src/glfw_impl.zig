/// this is a port of cimgui/imgui/examples/imgui_impl_glfw.cpp
const c = @import("c.zig");
const builtin = @import("builtin");
const debug = @import("std").debug;
const math = @import("std").math;

pub const ClientApi = enum {
    Unknown,
    OpenGL,
    Vulkan,
};

// Data
var g_Window: ?*c.GLFWwindow = null;
var g_ClientApi: ClientApi = .Unknown;
var g_Time: f64 = 0.0;
var g_MouseJustPressed = [_]bool{false} ** 5;
var g_MouseCursors = [_]?*c.GLFWcursor{null} ** c.ImGuiMouseCursor_COUNT;
var g_WantUpdateMonitors = true;

// Chain GLFW callbacks for main viewport:
// our callbacks will call the user's previously installed callbacks, if any.
var g_PrevUserCallbackMousebutton: c.GLFWmousebuttonfun = null;
var g_PrevUserCallbackScroll: c.GLFWscrollfun = null;
var g_PrevUserCallbackKey: c.GLFWkeyfun = null;
var g_PrevUserCallbackChar: c.GLFWcharfun = null;

pub fn Init(window: *c.GLFWwindow, install_callbacks: bool, client_api: ClientApi) void {
    g_Window = window;
    g_Time = 0.0;

    // Setup back-end capabilities flags
    const io = c.igGetIO();
    io.*.BackendFlags |= c.ImGuiBackendFlags_HasMouseCursors; // We can honor GetMouseCursor() values (optional)
    io.*.BackendFlags |= c.ImGuiBackendFlags_HasSetMousePos; // We can honor io.WantSetMousePos requests (optional, rarely used)
    if (false) io.*.BackendFlags |= c.ImGuiBackendFlags_PlatformHasViewports; // We can create multi-viewports on the Platform side (optional)
    if (false and @hasField(c, "GLFW_HAS_GLFW_HOVERED") and builtin.os == builtin.Os.windows) {
        io.*.BackendFlags |= ImGuiBackendFlags_HasMouseHoveredViewport; // We can set io.MouseHoveredViewport correctly (optional, not easy)
    }
    io.*.BackendPlatformName = "imgui_impl_glfw.zig";

    // Keyboard mapping. ImGui will use those indices to peek into the io.KeysDown[] array.
    io.*.KeyMap[c.ImGuiKey_Tab] = c.GLFW_KEY_TAB;
    io.*.KeyMap[c.ImGuiKey_LeftArrow] = c.GLFW_KEY_LEFT;
    io.*.KeyMap[c.ImGuiKey_RightArrow] = c.GLFW_KEY_RIGHT;
    io.*.KeyMap[c.ImGuiKey_UpArrow] = c.GLFW_KEY_UP;
    io.*.KeyMap[c.ImGuiKey_DownArrow] = c.GLFW_KEY_DOWN;
    io.*.KeyMap[c.ImGuiKey_PageUp] = c.GLFW_KEY_PAGE_UP;
    io.*.KeyMap[c.ImGuiKey_PageDown] = c.GLFW_KEY_PAGE_DOWN;
    io.*.KeyMap[c.ImGuiKey_Home] = c.GLFW_KEY_HOME;
    io.*.KeyMap[c.ImGuiKey_End] = c.GLFW_KEY_END;
    io.*.KeyMap[c.ImGuiKey_Insert] = c.GLFW_KEY_INSERT;
    io.*.KeyMap[c.ImGuiKey_Delete] = c.GLFW_KEY_DELETE;
    io.*.KeyMap[c.ImGuiKey_Backspace] = c.GLFW_KEY_BACKSPACE;
    io.*.KeyMap[c.ImGuiKey_Space] = c.GLFW_KEY_SPACE;
    io.*.KeyMap[c.ImGuiKey_Enter] = c.GLFW_KEY_ENTER;
    io.*.KeyMap[c.ImGuiKey_Escape] = c.GLFW_KEY_ESCAPE;
    io.*.KeyMap[c.ImGuiKey_KeyPadEnter] = c.GLFW_KEY_KP_ENTER;
    io.*.KeyMap[c.ImGuiKey_A] = c.GLFW_KEY_A;
    io.*.KeyMap[c.ImGuiKey_C] = c.GLFW_KEY_C;
    io.*.KeyMap[c.ImGuiKey_V] = c.GLFW_KEY_V;
    io.*.KeyMap[c.ImGuiKey_X] = c.GLFW_KEY_X;
    io.*.KeyMap[c.ImGuiKey_Y] = c.GLFW_KEY_Y;
    io.*.KeyMap[c.ImGuiKey_Z] = c.GLFW_KEY_Z;

    // @TODO: Clipboard
    // io.SetClipboardTextFn = ImGui_ImplGlfw_SetClipboardText;
    // io.GetClipboardTextFn = ImGui_ImplGlfw_GetClipboardText;
    io.*.ClipboardUserData = g_Window;

    g_MouseCursors[c.ImGuiMouseCursor_Arrow] = c.glfwCreateStandardCursor(c.GLFW_ARROW_CURSOR);
    g_MouseCursors[c.ImGuiMouseCursor_TextInput] = c.glfwCreateStandardCursor(c.GLFW_IBEAM_CURSOR);
    g_MouseCursors[c.ImGuiMouseCursor_ResizeAll] = c.glfwCreateStandardCursor(c.GLFW_ARROW_CURSOR); // FIXME: GLFW doesn't have this.
    g_MouseCursors[c.ImGuiMouseCursor_ResizeNS] = c.glfwCreateStandardCursor(c.GLFW_VRESIZE_CURSOR);
    g_MouseCursors[c.ImGuiMouseCursor_ResizeEW] = c.glfwCreateStandardCursor(c.GLFW_HRESIZE_CURSOR);
    g_MouseCursors[c.ImGuiMouseCursor_ResizeNESW] = c.glfwCreateStandardCursor(c.GLFW_ARROW_CURSOR); // FIXME: GLFW doesn't have this.
    g_MouseCursors[c.ImGuiMouseCursor_ResizeNWSE] = c.glfwCreateStandardCursor(c.GLFW_ARROW_CURSOR); // FIXME: GLFW doesn't have this.
    g_MouseCursors[c.ImGuiMouseCursor_Hand] = c.glfwCreateStandardCursor(c.GLFW_HAND_CURSOR);

    // Chain GLFW callbacks: our callbacks will call the user's previously installed callbacks, if any.
    g_PrevUserCallbackMousebutton = null;
    g_PrevUserCallbackScroll = null;
    g_PrevUserCallbackKey = null;
    g_PrevUserCallbackChar = null;
    if (install_callbacks) {
        g_PrevUserCallbackMousebutton = c.glfwSetMouseButtonCallback(window, Callback_MouseButton);
        g_PrevUserCallbackScroll = c.glfwSetScrollCallback(window, Callback_Scroll);
        g_PrevUserCallbackKey = c.glfwSetKeyCallback(window, Callback_Key);
        g_PrevUserCallbackChar = c.glfwSetCharCallback(window, Callback_Char);
    }

    // Our mouse update function expect PlatformHandle to be filled for the main viewport
    const main_viewport = c.igGetMainViewport();
    main_viewport.*.PlatformHandle = g_Window;
    // if (builtin.os == builtin.Os.windows)
    //     main_viewport.*.PlatformHandleRaw = c.glfwGetWin32Window(g_Window);

    // @TODO: Platform Interface (Viewport)
    if (io.*.ConfigFlags & c.ImGuiConfigFlags_ViewportsEnable != 0)
        unreachable;
    // ImGui_ImplGlfw_InitPlatformInterface();

    g_ClientApi = client_api;
}

pub fn Shutdown() void {
    // @TODO: Platform Interface (Viewport)
    // ImGui_ImplGlfw_ShutdownPlatformInterface();
    for (g_MouseCursors) |*cursor| {
        c.glfwDestroyCursor(cursor.*);
        cursor.* = null;
    }
    g_ClientApi = .Unknown;
}

pub fn NewFrame() void {
    const io = c.igGetIO();
    debug.assert(c.ImFontAtlas_IsBuilt(io.*.Fonts));

    var w: c_int = undefined;
    var h: c_int = undefined;
    var display_w: c_int = undefined;
    var display_h: c_int = undefined;
    c.glfwGetWindowSize(g_Window, &w, &h);
    c.glfwGetFramebufferSize(g_Window, &display_w, &display_h);
    io.*.DisplaySize = c.ImVec2{ .x = @intToFloat(f32, w), .y = @intToFloat(f32, h) };

    if (w > 0 and h > 0)
        io.*.DisplayFramebufferScale = c.ImVec2{
            .x = @intToFloat(f32, display_w) / @intToFloat(f32, w),
            .y = @intToFloat(f32, display_h) / @intToFloat(f32, h),
        };
    if (g_WantUpdateMonitors)
        UpdateMonitors();

    // Setup time step
    const current_time = c.glfwGetTime();
    io.*.DeltaTime = if (g_Time > 0.0) @floatCast(f32, current_time - g_Time) else 1.0 / 60.0;
    g_Time = current_time;

    UpdateMousePosAndButtons();
    UpdateMouseCursor();

    UpdateGamepads();
}

fn UpdateMonitors() void {
    const platform_io = c.igGetPlatformIO();
    var monitors_count: c_int = 0;
    const glfw_monitors = c.glfwGetMonitors(&monitors_count)[0..@intCast(usize, monitors_count)];

    c.ImVector_ImGuiPlatformMonitor_Resize(&platform_io.*.Monitors, monitors_count);
    for (glfw_monitors) |glfw_monitor, n| {
        var monitor = &platform_io.*.Monitors.Data[n];
        var x: c_int = undefined;
        var y: c_int = undefined;
        c.glfwGetMonitorPos(glfw_monitor, &x, &y);
        const vid_mode = c.glfwGetVideoMode(glfw_monitor); // glfw_monitors[n]);

        monitor.*.MainPos = c.ImVec2{ .x = @intToFloat(f32, x), .y = @intToFloat(f32, y) };
        monitor.*.MainSize = c.ImVec2{
            .x = @intToFloat(f32, vid_mode.*.width),
            .y = @intToFloat(f32, vid_mode.*.height),
        };
        if (false and c.GLFW_HAS_MONITOR_WORK_AREA) {
            var w: c_int = undefined;
            var h: c_int = undefined;
            c.glfwGetMonitorWorkarea(glfw_monitor, &x, &y, &w, &h);
            monitor.*.WorkPos = ImVec2{ .x = @intToFloat(f32, x), .y = @intToFloat(f32, y) };
            monitor.*.WorkSize = ImVec2{ .x = @intToFloat(f32, w), .y = @intToFloat(f32, h) };
        }
        if (false and c.GLFW_HAS_PER_MONITOR_DPI) {
            // Warning: the validity of monitor DPI information on Windows depends on the application DPI awareness settings,
            // which generally needs to be set in the manifest or at runtime.
            var x_scale: f32 = undefined;
            var y_scale: f32 = undefined;
            c.glfwGetMonitorContentScale(glfw_monitor, &x_scale, &y_scale);
            monitor.*.DpiScale = x_scale;
        }
    }
    g_WantUpdateMonitors = false;
}

fn UpdateMousePosAndButtons() void {
    // Update buttons
    const io = c.igGetIO();
    for (io.*.MouseDown) |*isDown, i| {
        // If a mouse press event came, always pass it as "mouse held this frame", so we don't miss click-release events that are shorter than 1 frame.
        isDown.* = g_MouseJustPressed[i] or c.glfwGetMouseButton(g_Window, @intCast(c_int, i)) != 0;
        g_MouseJustPressed[i] = false;
    }

    // Update mouse position
    const mouse_pos_backup = io.*.MousePos;
    io.*.MousePos = c.ImVec2{ .x = math.f32_min, .y = math.f32_min };
    io.*.MouseHoveredViewport = 0;
    const platform_io = c.igGetPlatformIO();
    var n: usize = 0;
    while (n < @intCast(usize, platform_io.*.Viewports.Size)) : (n += 1) {
        const viewport = platform_io.*.Viewports.Data[n];
        const window = @ptrCast(*c.GLFWwindow, viewport.*.PlatformHandle);
        const focused = c.glfwGetWindowAttrib(window, c.GLFW_FOCUSED) != 0;
        if (focused) {
            if (io.*.WantSetMousePos) {
                c.glfwSetCursorPos(window, mouse_pos_backup.x - viewport.*.Pos.x, mouse_pos_backup.y - viewport.*.Pos.y);
            } else {
                var mouse_x: f64 = undefined;
                var mouse_y: f64 = undefined;
                c.glfwGetCursorPos(window, &mouse_x, &mouse_y);
                if (io.*.ConfigFlags & c.ImGuiConfigFlags_ViewportsEnable != 0) {
                    // Multi-viewport mode: mouse position in OS absolute coordinates (io.MousePos is (0,0) when the mouse is on the upper-left of the primary monitor)
                    var window_x: c_int = undefined;
                    var window_y: c_int = undefined;
                    c.glfwGetWindowPos(window, &window_x, &window_y);
                    io.*.MousePos = c.ImVec2{
                        .x = @floatCast(f32, mouse_x) + @intToFloat(f32, window_x),
                        .y = @floatCast(f32, mouse_y) + @intToFloat(f32, window_y),
                    };
                } else {
                    // Single viewport mode: mouse position in client window coordinates (io.MousePos is (0,0) when the mouse is on the upper-left corner of the app window)
                    io.*.MousePos = c.ImVec2{ .x = @floatCast(f32, mouse_x), .y = @floatCast(f32, mouse_y) };
                }
            }

            for (io.*.MouseDown) |*isDown, i|
                isDown.* = isDown.* or c.glfwGetMouseButton(window, @intCast(c_int, i)) != 0;
        }
    }
}

fn UpdateMouseCursor() void {
    const io = c.igGetIO();
    if (io.*.ConfigFlags & c.ImGuiConfigFlags_NoMouseCursorChange != 0 or c.glfwGetInputMode(g_Window, c.GLFW_CURSOR) == c.GLFW_CURSOR_DISABLED)
        return;

    const imgui_cursor = c.igGetMouseCursor();
    const platform_io = c.igGetPlatformIO();
    var n: usize = 0;
    while (n < @intCast(usize, platform_io.*.Viewports.Size)) : (n += 1) {
        const window = @ptrCast(*c.GLFWwindow, platform_io.*.Viewports.Data[n].*.PlatformHandle);
        if (imgui_cursor == c.ImGuiMouseCursor_None or io.*.MouseDrawCursor) {
            // Hide OS mouse cursor if imgui is drawing it or if it wants no cursor
            c.glfwSetInputMode(window, c.GLFW_CURSOR, c.GLFW_CURSOR_HIDDEN);
        } else {
            // Show OS mouse cursor
            // FIXME-PLATFORM: Unfocused windows seems to fail changing the mouse cursor with GLFW 3.2, but 3.3 works here.
            c.glfwSetCursor(window, if (g_MouseCursors[@intCast(usize, imgui_cursor)]) |cursor| cursor else g_MouseCursors[c.ImGuiMouseCursor_Arrow]);
            c.glfwSetInputMode(window, c.GLFW_CURSOR, c.GLFW_CURSOR_NORMAL);
        }
    }
}

fn UpdateGamepads() void {
    // @TODO
}

// GLFW Callbacks
export fn Callback_MouseButton(window: ?*c.GLFWwindow, button: c_int, action: c_int, mods: c_int) void {
    if (g_PrevUserCallbackMousebutton) |prev| {
        prev(window, button, action, mods);
    }

    if (button < 0)
        return;

    const button_u = @intCast(usize, button);
    if (action == c.GLFW_PRESS and button_u < g_MouseJustPressed.len)
        g_MouseJustPressed[button_u] = true;
}

export fn Callback_Scroll(window: ?*c.GLFWwindow, dx: f64, dy: f64) void {
    if (g_PrevUserCallbackScroll) |prev| {
        prev(window, dx, dy);
    }

    const io = c.igGetIO();
    io.*.MouseWheelH += @floatCast(f32, dx);
    io.*.MouseWheel += @floatCast(f32, dy);
}

export fn Callback_Key(window: ?*c.GLFWwindow, key: c_int, scancode: c_int, action: c_int, modifiers: c_int) void {
    if (g_PrevUserCallbackKey) |prev| {
        prev(window, key, scancode, action, modifiers);
    }

    if (key < 0)
        unreachable;

    const key_u = @intCast(usize, key);

    const io = c.igGetIO();
    if (action == c.GLFW_PRESS)
        io.*.KeysDown[key_u] = true;
    if (action == c.GLFW_RELEASE)
        io.*.KeysDown[key_u] = false;

    // Modifiers are not reliable across systems
    io.*.KeyCtrl = io.*.KeysDown[c.GLFW_KEY_LEFT_CONTROL] or io.*.KeysDown[c.GLFW_KEY_RIGHT_CONTROL];
    io.*.KeyShift = io.*.KeysDown[c.GLFW_KEY_LEFT_SHIFT] or io.*.KeysDown[c.GLFW_KEY_RIGHT_SHIFT];
    io.*.KeyAlt = io.*.KeysDown[c.GLFW_KEY_LEFT_ALT] or io.*.KeysDown[c.GLFW_KEY_RIGHT_ALT];
    io.*.KeySuper = io.*.KeysDown[c.GLFW_KEY_LEFT_SUPER] or io.*.KeysDown[c.GLFW_KEY_RIGHT_SUPER];
}

export fn Callback_Char(window: ?*c.GLFWwindow, char: c_uint) void {
    if (g_PrevUserCallbackChar) |prev| {
        prev(window, char);
    }

    const io = c.igGetIO();
    c.ImGuiIO_AddInputCharacter(io, char);
}
