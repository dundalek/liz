const std = @import("std");
const expect = std.testing.expect;

// Try toggling these
const simulate_fail_download = false;
const simulate_fail_file = false;
const suspend_download = true;
const suspend_file = true;

pub fn main() void {
    _ = async amainWrap();
    // This simulates an event loop
    if (suspend_file) {
        resume global_file_frame;
    }
    if (suspend_download) {
        resume global_download_frame;
    }
}
fn amainWrap() void {
    if (amain()) |_| {
        expect(!simulate_fail_download);
        expect(!simulate_fail_file);
    } else |e| switch (e) {
        error.NoResponse => expect(simulate_fail_download),
        error.FileNotFound => expect(simulate_fail_file),
        else => @panic("test failure"),
    }
}

fn amain() !void {
    const allocator = std.heap.page_allocator;
    var download_frame = async fetchUrl(allocator, "https://example.com/");
    var download_awaited = false;
    errdefer if (!download_awaited) {
        if (await download_frame) |x| allocator.free(x) else |_| {}
    };

    var file_frame = async readFile(allocator, "something.txt");
    var file_awaited = false;
    errdefer if (!file_awaited) {
        if (await file_frame) |x| allocator.free(x) else |_| {}
    };

    download_awaited = true;
    const download_text = try await download_frame;
    defer allocator.free(download_text);

    file_awaited = true;
    const file_text = try await file_frame;
    defer allocator.free(file_text);

    expect(std.mem.eql(u8, "expected download text", download_text));
    expect(std.mem.eql(u8, "expected file text", file_text));
    std.debug.warn("OK!\n", .{});
}

var global_download_frame: anyframe = undefined;
fn fetchUrl(allocator: *std.mem.Allocator, url: []const u8) anyerror![]u8 {
    const result = try std.mem.dupe(allocator, u8, "expected download text");
    errdefer allocator.free(result);
    if (suspend_download) {
        suspend {
            global_download_frame = @frame();
        }
    }
    if (simulate_fail_download) return error.NoResponse;
    std.debug.warn("fetchUrl returning\n", .{});
    return result;
}

var global_file_frame: anyframe = undefined;
fn readFile(allocator: *std.mem.Allocator, filename: []const u8) anyerror![]u8 {
    const result = try std.mem.dupe(allocator, u8, "expected file text");
    errdefer allocator.free(result);
    if (suspend_file) {
        suspend {
            global_file_frame = @frame();
        }
    }
    if (simulate_fail_file) return error.FileNotFound;
    std.debug.warn("readFile returning\n", .{});
    return result;
}
