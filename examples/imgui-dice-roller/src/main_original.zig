/// this is a port of cimgui/imgui/examples/example_glfw_opengl3/main.cpp
const c = @import("c.zig");
const std = @import("std");
const panic = std.debug.panic;
const debug_gl = @import("debug_gl.zig");
const glfw_impl = @import("glfw_impl.zig");
const gl3_impl = @import("gl3_impl.zig");

export fn errorCallback(err: c_int, description: [*c]const u8) void {
    panic("Error: {}\n", .{description});
}

var rand: std.rand.DefaultPrng = undefined;

fn initRandom() !void {
    var buf: [8]u8 = undefined;
    try std.crypto.randomBytes(buf[0..]);
    const seed = std.mem.readIntLittle(u64, buf[0..8]);
    rand = std.rand.DefaultPrng.init(seed);
}

fn rgbColor(col: u32) u32 {
    return rgbaColor(col << 8 | 0xFF);
}

fn rgbaColor(col: u32) u32 {
    // ImGui format: a b g r
    // our HTML-like format: r g b a
    const a = (col & 0xFF) << 24;
    const b = (col & 0xFF00) << 8;
    const g = (col & 0xFF0000) >> 8;
    const r = (col & 0xFF000000) >> 24;

    return r | g | b | a;
}

const Dice = struct {
    sides: u32,
    color: u32,
    color_hovered: u32,
};

const Roll = struct {
    roll: u32,
    dice: usize,
};

const dice = [_]Dice{
    makeDice(2, 0xC9D127),
    makeDice(4, 0x24A146),
    makeDice(6, 0x27BCD1),
    makeDice(8, 0x9334E6),
    makeDice(10, 0xE13294),
    makeDice(12, 0xD93025),
    makeDice(20, 0xF36D00),
    makeDice(100, 0x878787),
};

fn makeDice(sides: u32, color: u32) Dice {
    return .{
        .sides = sides,
        .color = rgbaColor(color << 8 | 0xCC),
        .color_hovered = rgbColor(color),
    };
}

const max_rolls: usize = 20;
var rolls: [max_rolls]Roll = undefined;
var rolls_len: usize = 0;

fn doRoll() void {
    var i: usize = 0;
    while (i < rolls_len) : (i += 1) {
        var roll = &rolls[i];
        const d = dice[roll.dice];
        roll.roll = rand.random.uintLessThan(u32, d.sides) + 1;
    }
}

fn render() !void {
    c.igSetNextWindowSizeXY(500, 300, c.ImGuiCond_Once);
    // c.igSetNextWindowPos(10, 10, c.ImGuiCond_Once)

    var p_open = false;
    _ = c.igBegin("Dice Roller", &p_open, c.ImGuiWindowFlags_None);

    c.igText("Rolls");
    c.igPushIDStr("Rolls buttons");
    {
        var i: usize = 0;
        while (i < rolls_len) : (i += 1) {
            const roll = rolls[i];
            const d = dice[roll.dice];
            c.igPushIDInt(@intCast(c_int, i));
            c.igPushStyleColorU32(c.ImGuiCol_Button, d.color);
            c.igPushStyleColorU32(c.ImGuiCol_ButtonHovered, d.color_hovered);
            c.igPushStyleColorU32(c.ImGuiCol_ButtonActive, d.color);

            var buf: [32]u8 = undefined;
            _ = try std.fmt.bufPrint(&buf, "{}", .{roll.roll});
            if (c.igButtonXY(&buf, 50, 50) and rolls_len > 1) {
                var j: usize = i;
                while (j < rolls_len - 1) : (j += 1) {
                    rolls[j] = rolls[j + 1];
                }
                rolls_len -= 1;
            }
            c.igPopStyleColor(3);
            if (i + 1 < rolls_len) {
                c.igSameLine(0, -1);
            }
            c.igPopID();
        }
    }
    c.igPopID();

    c.igText("Add dice");
    c.igPushIDStr("Dice buttons");
    for (dice) |d, i| {
        c.igPushIDInt(@intCast(c_int, i));

        c.igPushStyleColorU32(c.ImGuiCol_Button, d.color);
        c.igPushStyleColorU32(c.ImGuiCol_ButtonHovered, d.color_hovered);
        c.igPushStyleColorU32(c.ImGuiCol_ButtonActive, d.color);

        var buf: [32]u8 = undefined;
        _ = try std.fmt.bufPrint(&buf, "{}", .{d.sides});
        if (c.igButtonXY(&buf, 50, 50)) {
            std.debug.warn("Adding {}: #{}, {}\n", .{ rolls_len, i, d });
            if (rolls_len < max_rolls) {
                rolls[rolls_len] = .{
                    .roll = rand.random.uintLessThan(u32, d.sides) + 1,
                    .dice = i,
                };
                rolls_len += 1;
            }
        }
        c.igPopStyleColor(3);
        if (i < dice.len - 1) {
            c.igSameLine(0, -1);
        }
        c.igPopID();
    }
    c.igPopID();

    {
        var total: u32 = 0;
        var i: usize = 0;
        while (i < rolls_len) : (i += 1) {
            total += rolls[i].roll;
        }
        var buf: [32]u8 = undefined;
        _ = try std.fmt.bufPrint(buf[0..], "Total: {}", .{total});
        c.igText(buf[0..]);
    }

    if (c.igButtonXY("Roll", 0, 0)) {
        doRoll();
    }

    c.igEnd();
}

pub fn main() !void {
    try initRandom();

    rolls[0] = .{
        .roll = 0,
        .dice = 2,
    };
    rolls_len = 1;
    doRoll();

    _ = c.glfwSetErrorCallback(errorCallback);

    if (c.glfwInit() == c.GL_FALSE) {
        panic("GLFW init failure\n", .{});
    }
    defer c.glfwTerminate();

    c.glfwWindowHint(c.GLFW_CONTEXT_VERSION_MAJOR, 3);
    c.glfwWindowHint(c.GLFW_CONTEXT_VERSION_MINOR, 2);
    c.glfwWindowHint(c.GLFW_OPENGL_FORWARD_COMPAT, c.GL_TRUE);
    c.glfwWindowHint(c.GLFW_OPENGL_DEBUG_CONTEXT, debug_gl.is_on);
    c.glfwWindowHint(c.GLFW_OPENGL_PROFILE, c.GLFW_OPENGL_CORE_PROFILE);
    // c.glfwWindowHint(c.GLFW_DEPTH_BITS, 0);
    // c.glfwWindowHint(c.GLFW_STENCIL_BITS, 8);
    c.glfwWindowHint(c.GLFW_RESIZABLE, c.GL_TRUE);

    const window_width = 640;
    const window_height = 480;
    const window = c.glfwCreateWindow(window_width, window_height, "ImGUI Test", null, null) orelse {
        panic("unable to create window\n", .{});
    };
    defer c.glfwDestroyWindow(window);

    c.glfwMakeContextCurrent(window);
    c.glfwSwapInterval(1);

    const context = c.igCreateContext(null);
    defer c.igDestroyContext(context);

    const io = c.igGetIO();
    io.*.ConfigFlags |= c.ImGuiConfigFlags_NavEnableKeyboard;
    // io.*.ConfigFlags |= @enumToInt(c.ImGuiConfigFlags_DockingEnable);
    // io.*.ConfigFlags |= @enumToInt(c.ImGuiConfigFlags_ViewportsEnable);

    const style = c.igGetStyle();
    c.igStyleColorsDark(style);

    // if (false and io.*.ConfigFlags & c.ImGuiConfigFlags_ViewportsEnable != 0) {
    //     style.*.WindowRounding = 0.0;
    //     style.*.Colors[c.ImGuiCol_WindowBg].w = 1.0;
    // }

    glfw_impl.Init(window, true, glfw_impl.ClientApi.OpenGL);
    defer glfw_impl.Shutdown();

    gl3_impl.Init(); // #version 150
    defer gl3_impl.Shutdown();

    const start_time = c.glfwGetTime();
    var prev_time = start_time;

    while (c.glfwWindowShouldClose(window) == c.GL_FALSE) {
        c.glfwPollEvents();

        try gl3_impl.NewFrame();
        glfw_impl.NewFrame();
        c.igNewFrame();

        try render();

        // main part
        // c.igShowDemoWindow(null);

        c.igRender();
        var w: c_int = undefined;
        var h: c_int = undefined;
        c.glfwGetFramebufferSize(window, &w, &h);
        c.glViewport(0, 0, w, h);
        c.glClearColor(0.0, 0.0, 0.0, 0.0);
        c.glClear(c.GL_COLOR_BUFFER_BIT);
        gl3_impl.RenderDrawData(c.igGetDrawData());

        // const now_time = c.glfwGetTime();
        // const elapsed = now_time - prev_time;
        // prev_time = now_time;
        // nextFrame(t, elapsed);
        // draw(t, @This());

        c.glfwSwapBuffers(window);
    }
}
