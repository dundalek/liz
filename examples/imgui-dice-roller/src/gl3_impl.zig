/// this is a port of cimgui/imgui/examples/imgui_impl_opengl3.cpp
const c = @import("c.zig");
const mem = @import("std").mem;
const math = @import("std").math;
const debug = @import("std").debug;
const builtin = @import("builtin");

const OpenGLHasDrawWithBaseVertex = @hasField(c, "IMGUI_IMPL_OPENGL_ES2") or
    @hasField(c, "IMGUI_IMPL_OPENGL_ES3");

// OpenGL Data
var g_GlslVersionString_buf: [32]u8 = undefined;
var g_GlslVersionString: []u8 = g_GlslVersionString_buf[0..0];

var g_FontTexture: c.GLuint = 0;
var g_ShaderHandle: c.GLuint = 0;
var g_VertHandle: c.GLuint = 0;
var g_FragHandle: c.GLuint = 0;
var g_AttribLocationTex: c.GLint = 0;
var g_AttribLocationProjMtx: c.GLint = 0;
var g_AttribLocationVtxPos: c.GLint = 0;
var g_AttribLocationVtxUV: c.GLint = 0;
var g_AttribLocationVtxColor: c.GLint = 0;
var g_VboHandle: c.GLuint = 0;
var g_ElementsHandle: c.GLuint = 0;

pub fn Init() void {
    const io = c.igGetIO();
    io.*.BackendRendererName = "imgui_impl_gl3.zig";
    if (OpenGLHasDrawWithBaseVertex)
        io.*.BackendFlags |= c.ImGuiBackendFlags_RendererHasVtxOffset;
    // @TODO: Viewports
    // io.*.BackendFlags |= @enumToInt(c.ImGuiBackendFlags_RendererHasViewports);

    // @TODO: GLSL versions?
    // g_GlslVersionString = g_GlslVersionString_buf[0..glsl_version.len];
    // mem.copy(u8, g_GlslVersionString, glsl_version);

    // @FIXME: just for testing:
    var tex: c.GLint = undefined;
    c.glGetIntegerv(c.GL_TEXTURE_BINDING_2D, &tex);

    // @TODO: Viewports
    // if (io.*.ConfigFlags & @enumToInt(c.ImGuiConfigFlags_ViewportsEnable) != 0)
    //   InitPlatformInterface();
}

pub fn Shutdown() void {
    // ImGui_ImplOpenGL3_ShutdownPlatformInterface();
    DestroyDeviceObjects();
}

pub fn NewFrame() !void {
    if (g_ShaderHandle == 0)
        try CreateDeviceObjects();
}

fn CreateDeviceObjects() !void {
    // back up GL state
    var last_texture: c.GLint = undefined;
    var last_array_buffer: c.GLint = undefined;
    var last_vertex_array: c.GLint = undefined;

    c.glGetIntegerv(c.GL_TEXTURE_BINDING_2D, &last_texture);
    defer c.glBindTexture(c.GL_TEXTURE_2D, @intCast(c.GLuint, last_texture));

    c.glGetIntegerv(c.GL_ARRAY_BUFFER_BINDING, &last_array_buffer);
    c.glBindBuffer(c.GL_ARRAY_BUFFER, @intCast(c.GLuint, last_array_buffer));

    if (!@hasField(c, "IMGUI_IMPL_OPENGL_ES2"))
        c.glGetIntegerv(c.GL_VERTEX_ARRAY_BINDING, &last_vertex_array);
    defer if (!@hasField(c, "IMGUI_IMPL_OPENGL_ES2"))
        c.glBindVertexArray(@intCast(c.GLuint, last_vertex_array));

    // @TODO: GLSL versions?
    const vertex_shader_glsl: [*]const c.GLchar =
        \\#version 150
        \\uniform mat4 ProjMtx;
        \\in vec2 Position;
        \\in vec2 UV;
        \\in vec4 Color;
        \\out vec2 Frag_UV;
        \\out vec4 Frag_Color;
        \\void main()
        \\{
        \\    Frag_UV = UV;
        \\    Frag_Color = Color;
        \\    gl_Position = ProjMtx * vec4(Position.xy, 0, 1);
        \\}
    ;

    const fragment_shader_glsl: [*]const c.GLchar =
        \\#version 150
        \\uniform sampler2D Texture;
        \\in vec2 Frag_UV;
        \\in vec4 Frag_Color;
        \\out vec4 Out_Color;
        \\void main()
        \\{
        \\    Out_Color = Frag_Color * texture(Texture, Frag_UV.st);
        \\}
    ;

    // Create shaders / programs
    g_VertHandle = c.glCreateShader(c.GL_VERTEX_SHADER);
    c.glShaderSource(g_VertHandle, 1, &vertex_shader_glsl, null);
    c.glCompileShader(g_VertHandle);
    try CheckThing(.Shader, g_VertHandle, "vertex shader");

    g_FragHandle = c.glCreateShader(c.GL_FRAGMENT_SHADER);
    c.glShaderSource(g_FragHandle, 1, &fragment_shader_glsl, null);
    c.glCompileShader(g_FragHandle);
    try CheckThing(.Shader, g_FragHandle, "fragment shader");

    g_ShaderHandle = c.glCreateProgram();
    c.glAttachShader(g_ShaderHandle, g_VertHandle);
    c.glAttachShader(g_ShaderHandle, g_FragHandle);
    c.glLinkProgram(g_ShaderHandle);
    try CheckThing(.Program, g_ShaderHandle, "shader program");

    g_AttribLocationTex = c.glGetUniformLocation(g_ShaderHandle, "Texture");
    g_AttribLocationProjMtx = c.glGetUniformLocation(g_ShaderHandle, "ProjMtx");
    g_AttribLocationVtxPos = c.glGetAttribLocation(g_ShaderHandle, "Position");
    g_AttribLocationVtxUV = c.glGetAttribLocation(g_ShaderHandle, "UV");
    g_AttribLocationVtxColor = c.glGetAttribLocation(g_ShaderHandle, "Color");

    // Create buffers
    c.glGenBuffers(1, &g_VboHandle);
    c.glGenBuffers(1, &g_ElementsHandle);

    CreateFontsTexture();
}

const CheckableThing = enum {
    Shader,
    Program,
};
fn CheckThing(comptime thingType: CheckableThing, handle: c.GLuint, desc: []const u8) !void {
    var status: c.GLint = undefined;
    var log_length: c.GLint = undefined;
    const getInfoLogFunc = switch (thingType) {
        .Shader => blk: {
            c.glGetShaderiv(handle, c.GL_COMPILE_STATUS, &status);
            c.glGetShaderiv(handle, c.GL_INFO_LOG_LENGTH, &log_length);
            break :blk c.glGetShaderInfoLog;
        },
        .Program => blk: {
            c.glGetProgramiv(handle, c.GL_LINK_STATUS, &status);
            c.glGetProgramiv(handle, c.GL_INFO_LOG_LENGTH, &log_length);
            break :blk c.glGetProgramInfoLog;
        },
    };

    if (log_length > 1) {
        var buf: [1024]u8 = undefined;
        var length: c.GLsizei = undefined;
        getInfoLogFunc(handle, buf.len, &length, &buf[0]);
        debug.warn("{}\n", .{buf[0..@intCast(usize, length)]});
    }

    if (@intCast(c.GLboolean, status) == c.GL_FALSE) {
        debug.warn("ERROR: CreateDeviceObjects: failed to compile/link {}! (with GLSL '{}')\n", .{desc, g_GlslVersionString});
        return error.ShaderLinkError;
    }
}

fn DestroyDeviceObjects() void {
    if (g_VboHandle != 0) {
        c.glDeleteBuffers(1, &g_VboHandle);
        g_VboHandle = 0;
    }

    if (g_ElementsHandle != 0) {
        c.glDeleteBuffers(1, &g_ElementsHandle);
        g_ElementsHandle = 0;
    }

    if (g_ShaderHandle != 0 and g_VertHandle != 0)
        c.glDetachShader(g_ShaderHandle, g_VertHandle);

    if (g_ShaderHandle != 0 and g_FragHandle != 0)
        c.glDetachShader(g_ShaderHandle, g_FragHandle);

    if (g_VertHandle != 0) {
        c.glDeleteShader(g_VertHandle);
        g_VertHandle = 0;
    }

    if (g_FragHandle != 0) {
        c.glDeleteShader(g_FragHandle);
        g_FragHandle = 0;
    }

    if (g_ShaderHandle != 0) {
        c.glDeleteProgram(g_ShaderHandle);
        g_ShaderHandle = 0;
    }

    DestroyFontsTexture();
}

fn CreateFontsTexture() void {
    const io = c.igGetIO();

    // Get current font image data
    var width: c_int = undefined;
    var height: c_int = undefined;
    var pixels: [*c]u8 = undefined;
    c.ImFontAtlas_GetTexDataAsRGBA32(io.*.Fonts, &pixels, &width, &height, null);

    // backup & restore state
    var last_texture: c.GLint = undefined;
    c.glGetIntegerv(c.GL_TEXTURE_BINDING_2D, &last_texture);
    defer c.glBindTexture(c.GL_TEXTURE_2D, @intCast(c.GLuint, last_texture));

    // Upload texture to graphics system
    c.glGenTextures(1, &g_FontTexture);
    c.glBindTexture(c.GL_TEXTURE_2D, g_FontTexture);
    c.glTexParameteri(c.GL_TEXTURE_2D, c.GL_TEXTURE_MIN_FILTER, c.GL_LINEAR);
    c.glTexParameteri(c.GL_TEXTURE_2D, c.GL_TEXTURE_MAG_FILTER, c.GL_LINEAR);
    if (@hasField(c, "GL_UNPACK_ROW_LENGTH"))
        c.glPixelStorei(c.GL_UNPACK_ROW_LENGTH, 0);
    c.glTexImage2D(c.GL_TEXTURE_2D, 0, c.GL_RGBA, width, height, 0, c.GL_RGBA, c.GL_UNSIGNED_BYTE, pixels);

    // Store texture ID
    io.*.Fonts.*.TexID = @intToPtr(c.ImTextureID, g_FontTexture);
}

fn DestroyFontsTexture() void {
    if (g_FontTexture == 0)
        return;

    const io = c.igGetIO();
    c.glDeleteTextures(1, &g_FontTexture);
    io.*.Fonts.*.TexID = @intToPtr(c.ImTextureID, 0);
    g_FontTexture = 0;
}

pub fn RenderDrawData(draw_data: *c.ImDrawData) void {
    // Avoid rendering when minimized, scale coordinates for retina displays (screen coordinates != framebuffer coordinates)
    const fb_width = @floatToInt(i32, draw_data.*.DisplaySize.x * draw_data.*.FramebufferScale.x);
    const fb_height = @floatToInt(i32, draw_data.*.DisplaySize.y * draw_data.*.FramebufferScale.y);
    if (fb_width <= 0 or fb_height <= 0)
        return;

    // Backup GL state
    var last_program: c.GLint = undefined;
    var last_texture: c.GLint = undefined;
    var last_sampler: c.GLint = undefined;
    var last_array_buffer: c.GLint = undefined;
    var last_vertex_array_object: c.GLint = undefined;
    var last_polygon_mode: [2]c.GLint = undefined;
    var last_viewport: [4]c.GLint = undefined;
    var last_scissor_box: [4]c.GLint = undefined;
    var last_blend_src_rgb: c.GLint = undefined;
    var last_blend_dst_rgb: c.GLint = undefined;
    var last_blend_src_alpha: c.GLint = undefined;
    var last_blend_dst_alpha: c.GLint = undefined;
    var last_blend_equation_rgb: c.GLint = undefined;
    var last_blend_equation_alpha: c.GLint = undefined;
    var clip_origin_lower_left: bool = true;
    var last_clip_origin: c.GLint = 0;
    var last_active_texture: c.GLint = undefined;

    c.glGetIntegerv(c.GL_ACTIVE_TEXTURE, &last_active_texture);
    c.glActiveTexture(c.GL_TEXTURE0);
    c.glGetIntegerv(c.GL_CURRENT_PROGRAM, &last_program);
    c.glGetIntegerv(c.GL_TEXTURE_BINDING_2D, &last_texture);
    if (@hasField(c, "GL_SAMPLER_BINDING"))
        c.glGetIntegerv(c.GL_SAMPLER_BINDING, &last_sampler);
    c.glGetIntegerv(c.GL_ARRAY_BUFFER_BINDING, &last_array_buffer);
    if (!@hasField(c, "IMGUI_IMPL_OPENc.GL_ES2"))
        c.glGetIntegerv(c.GL_VERTEX_ARRAY_BINDING, &last_vertex_array_object);
    if (@hasField(c, "GL_POLYGON_MODE"))
        c.glGetIntegerv(c.GL_POLYGON_MODE, last_polygon_mode);
    c.glGetIntegerv(c.GL_VIEWPORT, &last_viewport[0]);
    c.glGetIntegerv(c.GL_SCISSOR_BOX, &last_scissor_box[0]);
    c.glGetIntegerv(c.GL_BLEND_SRC_RGB, &last_blend_src_rgb);
    c.glGetIntegerv(c.GL_BLEND_DST_RGB, &last_blend_dst_rgb);
    c.glGetIntegerv(c.GL_BLEND_SRC_ALPHA, &last_blend_src_alpha);
    c.glGetIntegerv(c.GL_BLEND_DST_ALPHA, &last_blend_dst_alpha);
    c.glGetIntegerv(c.GL_BLEND_EQUATION_RGB, &last_blend_equation_rgb);
    c.glGetIntegerv(c.GL_BLEND_EQUATION_ALPHA, &last_blend_equation_alpha);
    var last_enable_blend: c.GLboolean = c.glIsEnabled(c.GL_BLEND);
    var last_enable_cull_face: c.GLboolean = c.glIsEnabled(c.GL_CULL_FACE);
    var last_enable_depth_test: c.GLboolean = c.glIsEnabled(c.GL_DEPTH_TEST);
    var last_enable_scissor_test: c.GLboolean = c.glIsEnabled(c.GL_SCISSOR_TEST);
    if (@hasField(c, "GL_CLIP_ORIGIN") and builtin.os != builtin.Os.osx) {
        // Support for GL 4.5's glClipControl(GL_UPPER_LEFT)
        c.glGetIntegerv(c.GL_CLIP_ORIGIN, &last_clip_origin);
        if (last_clip_origin == c.GL_UPPER_LEFT)
            clip_origin_lower_left = false;
    }

    defer {
        // Restore modified GL state
        c.glUseProgram(@intCast(c.GLuint, last_program));
        c.glBindTexture(c.GL_TEXTURE_2D, @intCast(c.GLuint, last_texture));
        if (@hasField(c, "GL_SAMPLER_BINDING"))
            c.glBindSampler(0, @intCast(c.GLuint, last_sampler));
        c.glActiveTexture(@intCast(c.GLuint, last_active_texture));
        if (!@hasField(c, "IMGUI_IMPL_OPENGL_ES2"))
            c.glBindVertexArray(@intCast(c.GLuint, last_vertex_array_object));
        c.glBindBuffer(c.GL_ARRAY_BUFFER, @intCast(c.GLuint, last_array_buffer));
        c.glBlendEquationSeparate(@intCast(c.GLuint, last_blend_equation_rgb), @intCast(c.GLuint, last_blend_equation_alpha));
        c.glBlendFuncSeparate(@intCast(c.GLuint, last_blend_src_rgb), @intCast(c.GLuint, last_blend_dst_rgb), @intCast(c.GLuint, last_blend_src_alpha), @intCast(c.GLuint, last_blend_dst_alpha));
        if (last_enable_blend == c.GL_TRUE) {
            c.glEnable(c.GL_BLEND);
        } else {
            c.glDisable(c.GL_BLEND);
        }
        if (last_enable_cull_face == c.GL_TRUE) {
            c.glEnable(c.GL_CULL_FACE);
        } else {
            c.glDisable(c.GL_CULL_FACE);
        }
        if (last_enable_depth_test == c.GL_TRUE) {
            c.glEnable(c.GL_DEPTH_TEST);
        } else {
            c.glDisable(c.GL_DEPTH_TEST);
        }
        if (last_enable_scissor_test == c.GL_TRUE) {
            c.glEnable(c.GL_SCISSOR_TEST);
        } else {
            c.glDisable(c.GL_SCISSOR_TEST);
        }
        if (@hasField(c, "GL_POLYGON_MODE"))
            c.glPolygonMode(c.GL_FRONT_AND_BACK, @intCast(c.GLenum, last_polygon_mode[0]));
        c.glViewport(last_viewport[0], last_viewport[1], @intCast(c.GLsizei, last_viewport[2]), @intCast(c.GLsizei, last_viewport[3]));
        c.glScissor(last_scissor_box[0], last_scissor_box[1], @intCast(c.GLsizei, last_scissor_box[2]), @intCast(c.GLsizei, last_scissor_box[3]));
    }

    // Setup desired GL state
    // Recreate the VAO every time (this is to easily allow multiple GL contexts to be rendered to. VAO are not shared among GL contexts)
    // The renderer would actually work without any VAO bound, but then our VertexAttrib calls would overwrite the default one currently bound.
    var vertex_array_object: c.GLuint = 0;
    if (!@hasField(c, "IMGUI_IMPL_OPENGL_ES2"))
        c.glGenVertexArrays(1, &vertex_array_object);
    defer if (!@hasField(c, "IMGUI_IMPL_OPENGL_ES2"))
        c.glDeleteVertexArrays(1, &vertex_array_object);

    SetupRenderState(draw_data, fb_width, fb_height, vertex_array_object);

    // Will project scissor/clipping rectangles into framebuffer space
    const clip_off = draw_data.*.DisplayPos; // (0,0) unless using multi-viewports
    const clip_scale = draw_data.*.FramebufferScale; // (1,1) unless using retina display which are often (2,2)

    // Render command lists
    var n: usize = 0;
    while (n < @intCast(usize, draw_data.*.CmdListsCount)) : (n += 1) {
        const cmd_list = draw_data.*.CmdLists[n];

        // Upload vertex/index buffers
        c.glBufferData(c.GL_ARRAY_BUFFER, cmd_list.*.VtxBuffer.Size * @sizeOf(c.ImDrawVert), @ptrCast(?*c.GLvoid, cmd_list.*.VtxBuffer.Data), c.GL_STREAM_DRAW);
        c.glBufferData(c.GL_ELEMENT_ARRAY_BUFFER, cmd_list.*.IdxBuffer.Size * @sizeOf(c.ImDrawIdx), @ptrCast(*c.GLvoid, cmd_list.*.IdxBuffer.Data), c.GL_STREAM_DRAW);

        var cmd_i: usize = 0;
        while (cmd_i < @intCast(usize, cmd_list.*.CmdBuffer.Size)) : (cmd_i += 1) {
            const pcmd = &cmd_list.*.CmdBuffer.Data[cmd_i];

            if (pcmd.*.UserCallback != null) {
                // User callback, registered via ImDrawList::AddCallback()
                // (ImDrawCallback_ResetRenderState is a special callback value used by the user to request the renderer to reset render state.)
                const ImDrawCallback_ResetRenderState = @intToPtr(c.ImDrawCallback, math.maxInt(usize));
                if (pcmd.*.UserCallback == ImDrawCallback_ResetRenderState) {
                    SetupRenderState(draw_data, fb_width, fb_height, vertex_array_object);
                } else {
                    if (pcmd.*.UserCallback) |callback| {
                        callback(cmd_list, pcmd);
                    } else {
                        unreachable;
                    }
                }
            } else {
                // Project scissor/clipping rectangles into framebuffer space
                var clip_rect: c.ImVec4 = undefined;
                clip_rect.x = (pcmd.*.ClipRect.x - clip_off.x) * clip_scale.x;
                clip_rect.y = (pcmd.*.ClipRect.y - clip_off.y) * clip_scale.y;
                clip_rect.z = (pcmd.*.ClipRect.z - clip_off.x) * clip_scale.x;
                clip_rect.w = (pcmd.*.ClipRect.w - clip_off.y) * clip_scale.y;

                if (clip_rect.x < @intToFloat(f32, fb_width) and clip_rect.y < @intToFloat(f32, fb_height) and clip_rect.z >= 0.0 and clip_rect.w >= 0.0) {
                    // Apply scissor/clipping rectangle
                    if (clip_origin_lower_left) {
                        c.glScissor(@floatToInt(c.GLint, clip_rect.x), fb_height - @floatToInt(c.GLint, clip_rect.w), @floatToInt(c.GLint, clip_rect.z - clip_rect.x), @floatToInt(c.GLint, clip_rect.w - clip_rect.y));
                    } else {
                        // Support for GL 4.5 rarely used glClipControl(GL_UPPER_LEFT)
                        c.glScissor(@floatToInt(c.GLint, clip_rect.x), @floatToInt(c.GLint, clip_rect.y), @floatToInt(c.GLint, clip_rect.z), @floatToInt(c.GLint, clip_rect.w));
                    }

                    // Bind texture, Draw
                    c.glBindTexture(c.GL_TEXTURE_2D, @intCast(c.GLuint, @ptrToInt(pcmd.*.TextureId)));
                    const drawIndexSize = @sizeOf(c.ImDrawIdx);
                    const drawIndexType = if (drawIndexSize == 2) c.GL_UNSIGNED_SHORT else c.GL_UNSIGNED_INT;
                    const offset = @intToPtr(?*c.GLvoid, pcmd.*.IdxOffset * drawIndexSize);
                    if (@hasField(c, "IMGUI_IMPL_OPENGL_HAS_DRAW_WITH_BASE_VERTEX")) {
                        c.glDrawElementsBaseVertex(c.GL_TRIANGLES, @intCast(c.GLsizei, pcmd.*.ElemCount), drawIndexType, offset, @intCast(c.GLint, pcmd.*.VtxOffset));
                    } else {
                        c.glDrawElements(c.GL_TRIANGLES, @intCast(c.GLsizei, pcmd.*.ElemCount), drawIndexType, offset);
                    }
                }
            }
        }
    }
}

fn SetupRenderState(draw_data: *c.ImDrawData, fb_width: i32, fb_height: i32, vertex_array_object: c.GLuint) void {
    // Setup render state: alpha-blending enabled, no face culling, no depth testing, scissor enabled, polygon fill
    c.glEnable(c.GL_BLEND);
    c.glBlendEquation(c.GL_FUNC_ADD);
    c.glBlendFunc(c.GL_SRC_ALPHA, c.GL_ONE_MINUS_SRC_ALPHA);
    c.glDisable(c.GL_CULL_FACE);
    c.glDisable(c.GL_DEPTH_TEST);
    c.glEnable(c.GL_SCISSOR_TEST);
    if (@hasField(c, "GL_POLYGON_MODE"))
        c.glPolygonMode(c.GL_FRONT_AND_BACK, c.GL_FILL);

    // Setup viewport, orthographic projection matrix
    // Our visible imgui space lies from draw_data.*.DisplayPos (top left) to draw_data.*.DisplayPos+data_data.*.DisplaySize (bottom right). DisplayPos is (0,0) for single viewport apps.
    c.glViewport(0, 0, @intCast(c.GLsizei, fb_width), @intCast(c.GLsizei, fb_height));
    const L = draw_data.*.DisplayPos.x;
    const R = draw_data.*.DisplayPos.x + draw_data.*.DisplaySize.x;
    const T = draw_data.*.DisplayPos.y;
    const B = draw_data.*.DisplayPos.y + draw_data.*.DisplaySize.y;
    const ortho_projection = [4][4]f32{
        [_]f32{ 2.0 / (R - L), 0.0, 0.0, 0.0 },
        [_]f32{ 0.0, 2.0 / (T - B), 0.0, 0.0 },
        [_]f32{ 0.0, 0.0, -1.0, 0.0 },
        [_]f32{ (R + L) / (L - R), (T + B) / (B - T), 0.0, 1.0 },
    };
    c.glUseProgram(g_ShaderHandle);
    c.glUniform1i(g_AttribLocationTex, 0);
    c.glUniformMatrix4fv(g_AttribLocationProjMtx, 1, c.GL_FALSE, &ortho_projection[0][0]);
    if (@hasField(c, "GL_SAMPLER_BINDING"))
        c.glBindSampler(0, 0); // We use combined texture/sampler state. Applications using GL 3.3 may set that otherwise.

    if (!@hasField(c, "IMGUI_IMPL_OPENGL_ES2"))
        c.glBindVertexArray(vertex_array_object);

    // Bind vertex/index buffers and setup attributes for ImDrawVert
    c.glBindBuffer(c.GL_ARRAY_BUFFER, g_VboHandle);
    c.glBindBuffer(c.GL_ELEMENT_ARRAY_BUFFER, g_ElementsHandle);
    c.glEnableVertexAttribArray(@intCast(c.GLuint, g_AttribLocationVtxPos));
    c.glEnableVertexAttribArray(@intCast(c.GLuint, g_AttribLocationVtxUV));
    c.glEnableVertexAttribArray(@intCast(c.GLuint, g_AttribLocationVtxColor));
    c.glVertexAttribPointer(@intCast(c.GLuint, g_AttribLocationVtxPos), 2, c.GL_FLOAT, c.GL_FALSE, @sizeOf(c.ImDrawVert), @intToPtr(?*c.GLvoid, @byteOffsetOf(c.ImDrawVert, "pos")));
    c.glVertexAttribPointer(@intCast(c.GLuint, g_AttribLocationVtxUV), 2, c.GL_FLOAT, c.GL_FALSE, @sizeOf(c.ImDrawVert), @intToPtr(?*c.GLvoid, @byteOffsetOf(c.ImDrawVert, "uv")));
    c.glVertexAttribPointer(@intCast(c.GLuint, g_AttribLocationVtxColor), 4, c.GL_UNSIGNED_BYTE, c.GL_TRUE, @sizeOf(c.ImDrawVert), @intToPtr(?*c.GLvoid, @byteOffsetOf(c.ImDrawVert, "col")));
}
