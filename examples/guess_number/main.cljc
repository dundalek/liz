;; Port of: https://github.com/ziglang/zig/blob/77bb2dc094bfe9fff9208a8af94d0da617bdae13/test/standalone/guess_number/main.zig

(const builtin (@import "builtin"))
(const std (@import "std"))
(const io std.io)
(const fmt std.fmt)

(defn ^:pub ^!void main []
  (const stdout (.. io getStdOut outStream))
  (const stdin (.getStdIn io))

  (try (.print stdout "Welcome to the Guess Number Game in Zig.\n" []))

  (var ^"[@sizeOf(u64)]u8" seed_bytes undefined)
  (try
    (std.crypto.randomBytes (slice seed_bytes 0))
    (catch _ err
      (.warn std.debug "unable to seed random number generator: {}" [err])
      (return err)))

  (const seed (std.mem.readIntNative u64 (& seed_bytes)))
  (var prng (std.rand.DefaultPrng.init seed))

  (const answer (+ (prng.random.intRangeLessThan u8, 0, 100) 1))

  (while true
    (try (.print stdout "\nGuess a number between 1 and 100: " []))
    (var ^"[20]u8" line_buf undefined)

    (const amt (try (.read stdin (& line_buf))))
    (when (= amt line_buf.len)
        (try (.print stdout "Input too long.\n" []))
        (continue))

    (const line (.trimRight std.mem u8 (slice line_buf 0 amt) "\r\n"))

    (try
      (const guess (fmt.parseUnsigned u8 line 10))
      (catch _ _
        (try (.print stdout "Invalid number.\n" []))
        (continue)))

    (cond
      (> guess answer) (try (.print stdout "Guess lower.\n" []))
      (< guess answer) (try (.print stdout "Guess higher.\n" []))
      :else (do
              (try (.print stdout "You win!\n" []))
              (return)))))
