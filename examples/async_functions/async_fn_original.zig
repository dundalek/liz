const std = @import("std");

var frame: anyframe = undefined;

pub fn main() void {
    std.debug.warn("begin main\n", .{});
    _ = async func();
    std.debug.warn("resume func\n", .{});
    resume frame;
    std.debug.warn("end main\n", .{});
}

fn func() void {
    std.debug.warn("begin func\n", .{});
    frame = @frame();
    suspend;
    std.debug.warn("end func\n", .{});
}
