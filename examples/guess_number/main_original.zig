// Source: https://github.com/ziglang/zig/blob/77bb2dc094bfe9fff9208a8af94d0da617bdae13/test/standalone/guess_number/main.zig

const builtin = @import("builtin");
const std = @import("std");
const io = std.io;
const fmt = std.fmt;

pub fn main() !void {
    const stdout = io.getStdOut().outStream();
    const stdin = io.getStdIn();

    try stdout.print("Welcome to the Guess Number Game in Zig.\n", .{});

    var seed_bytes: [@sizeOf(u64)]u8 = undefined;
    std.crypto.randomBytes(seed_bytes[0..]) catch |err| {
        std.debug.warn("unable to seed random number generator: {}", .{err});
        return err;
    };
    const seed = std.mem.readIntNative(u64, &seed_bytes);
    var prng = std.rand.DefaultPrng.init(seed);

    const answer = prng.random.intRangeLessThan(u8, 0, 100) + 1;

    while (true) {
        try stdout.print("\nGuess a number between 1 and 100: ", .{});
        var line_buf: [20]u8 = undefined;

        const amt = try stdin.read(&line_buf);
        if (amt == line_buf.len) {
            try stdout.print("Input too long.\n", .{});
            continue;
        }
        const line = std.mem.trimRight(u8, line_buf[0..amt], "\r\n");

        const guess = fmt.parseUnsigned(u8, line, 10) catch {
            try stdout.print("Invalid number.\n", .{});
            continue;
        };
        if (guess > answer) {
            try stdout.print("Guess lower.\n", .{});
        } else if (guess < answer) {
            try stdout.print("Guess higher.\n", .{});
        } else {
            try stdout.print("You win!\n", .{});
            return;
        }
    }
}
