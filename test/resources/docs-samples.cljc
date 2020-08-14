;; == run hello
(const std (@import "std"))

(fn ^:pub ^!void main []
  (const stdout (.. std -io getStdOut outStream))
  (try (.print stdout "Hello, {}!\n" ["world"])))

;; == run hello_again
(const print (.. (@import "std") -debug -warn))

(fn ^:pub ^void main []
  (print "Hello, world!\n" []))

;; == test comments

(const assert (.. (@import "std") -debug -assert))

(test "comments"
  ;; (assert false)
  (const x true) ; another comment
  (assert x))

;; == run values
;; Top-level declarations are order-independent:
(const print std.debug.warn)
(const std (@import "std"))
(const os std.os)
(const assert std.debug.assert)

(fn ^:pub ^void main []
    ;; integers
    (const ^i32 one_plus_one (+ 1 1))
    (print "1 + 1 = {}\n" [one_plus_one])

    ;; floats
    (const ^f32 seven_div_three (/ 7.0 3.0))
    (print "7.0 / 3.0 = {}\n", [seven_div_three])

    ;; boolean
    (print "{}\n{}\n{}\n"
           [(and true false)
            (or true false,)
            (not true)])

    ;; optional
    (vari ^"?[]const u8" optional_value nil)
    (assert (= optional_value nil))

    (print "\noptional 1\ntype: {}\nvalue: {}\n"
      [(@typeName (@TypeOf optional_value))
       optional_value])

    (set! optional_value "hi")
    (assert (not= optional_value null))

    (print "\noptional 2\ntype: {}\nvalue: {}\n"
      [(@typeName (@TypeOf optional_value))
       optional_value])

    ;; error union
    (vari ^anyerror!i32 number_or_error error.ArgNotFound)

    (print "\nerror union 1\ntype: {}\nvalue: {}\n"
      [(@typeName (@TypeOf number_or_error))
       number_or_error])

    (set! number_or_error 1234)

    (print "\nerror union 2\ntype: {}\nvalue: {}\n"
      [(@typeName (@TypeOf number_or_error))
       number_or_error]))

;; == test string literals
(const assert (.. (@import "std") -debug -assert))
(const mem (.. (@import "std") -mem))

(test "string literals"
  (const bytes "hello")
  (assert (= (@TypeOf bytes) (zig* "*const [5:0]u8")))
  (assert (= bytes.len 5))
  (assert (= (aget bytes 1) \e))
  (assert (= (aget bytes 5) 0))
  (assert (= \e \u0065));
  (assert (= 0x1f4a9 128169))
  ; (assert (= \💯 128175));
  (assert (.eql mem u8 "hello" "h\u0065llo")))

;; == test namespaced_global.zig
(const std (@import "std"))
(const assert std.debug.assert)

(test "namespaced global variable"
  (assert (= (foo) 1235))
  (assert (= (foo) 1236)))

(fn ^i32 foo []
  (const S (struct
             ^:var ^i32 x 1234))
  (+= S.x 1)
  (return S.x))

;; == test Thread Local Variables
(const std (@import "std"))
(const assert std.debug.assert)

(vari ^:threadlocal ^i32 x 1234)

(test "thread local storage"
  (const thread1 (try (std.Thread.spawn {} testTls)))
  (const thread2 (try (std.Thread.spawn {} testTls)))
  (testTls {})
  (.wait thread1)
  (.wait thread2))

(fn ^void testTls [^void context]
  (assert (= x 1234))
  (+= x 1)
  (assert (= x 1235)))

;; == test comptime variables
(const std (@import "std"))
(const assert std.debug.assert)

(test "comptime vars"
  (vari ^i32 x 1)
  (vari ^:comptime ^i32 y 1)

  (+= x 1)
  (+= y 1)

  (assert (= x 2))
  (assert (= y 2))

  (when (not= y 2)
    ;; This compile error never triggers because y is a comptime variable,)
    ;; and so `y != 2` is a comptime value, and this if is statically evaluated.)
    (@compileError "wrong y value")))

;; == test Arrays
(const assert (.. (@import "std") -debug -assert))
(const mem (.. (@import "std") -mem))

;; array literal
(const message ^"[_]u8" [\h \e \l \l \o])

;; get the size of an array
(comptime
  (assert (= message.len 5)))

;; A string literal is a pointer to an array literal
(const same_message "hello")

(comptime
  (assert (.eql mem u8 (& message) same_message)))

(test "iterate over an array"
  (vari ^usize sum 0)
  (for [byte message]
    (+= sum byte))
  (assert (= sum (+ \h \e (* \l 2) \o))))

;; modifiable array
(vari ^"[100]i32" some_integers undefined)

(test "modify an array"
  (for [[*item, i] some_integers]
      (set! item.* (@intCast i32 i)))
  (assert (= (aget some_integers 10) 10))
  (assert (= (aget some_integers 99) 99)))

;; array concatenation works if the values are known
;; at compile time
(const part_one ^"[_]i32" [1, 2, 3, 4])
(const part_two ^"[_]i32" [5, 6, 7, 8])
(const all_of_it (++ part_one part_two))
(comptime
  (assert (.eql mem i32 (& all_of_it) (& ^"[_]i32" [1, 2, 3, 4, 5, 6, 7, 8]))))

;; remember that string literals are arrays
(const hello "hello")
(const world "world")
(const hello_world (++ hello " " world))
(comptime
  (assert (.eql mem u8 hello_world "hello world")))

;; ** does repeating patterns
(const pattern (** "ab" 3))
(comptime
  (assert (.eql mem u8 pattern "ababab")))

;; initialize an array to zero
(const all_zero (** ^"[_]u16" [0] 10))
(comptime
  (assert (= all_zero.len 10))
  (assert (= (aget all_zero 5) 0)))

;; use compile-time code to initialize an array
(vari fancy_array
  (block :init
    (vari ^"[10]Point" initial_value undefined)
    (for [[*pt, i] initial_value]
      (set! pt.* ^Point{:x (@intCast i32 i)
                        :y (* (@intCast i32 i) 2)}))
    (break :init initial_value)))

(const Point
  (struct
    :x i32
    :y i32))

(test "compile-time array initalization"
  (assert (= (.-x (aget fancy_array 4)) 4))
  (assert (= (.-y (aget fancy_array 4)) 8)))

;; call a function to initialize an array
(vari more_points (-> ^"[_]Point" [(makePoint 3)]
                     (** 10)))
(fn ^Point makePoint [^i32 x]
  (return ^Point {:x x,
                  :y (* x 2)}))

(test "array initialization with function calls"
  (assert (= (.-x (aget more_points 4)) 3))
  (assert (= (.-y (aget more_points 4)) 6))
  (assert (= more_points.len 10)));

;; == test infer_list_literal.zig
(const std (@import "std"))
(const assert std.debug.assert)

(test "fully anonymous list literal"
  (dump [(@as u32 1234), (@as f64 12.34), true, "hi"]))

(fn ^void dump [^var args]
  (assert (= (.-0 args) 1234))
  (assert (= (.-1 args) 12.34))
  (assert (.-2 args))
  (assert (= (aget (.-3 args) 0) \h))
  (assert (= (aget (.-3 args) 1) \i)))

;; == test multidimensional.zig
(const std (@import "std"))
(const assert std.debug.assert)

(const mat4x4 ^"[4][4]f32" [^"[_]f32" [1.0, 0.0, 0.0, 0.0]
                            ^"[_]f32" [0.0, 1.0, 0.0, 1.0]
                            ^"[_]f32" [0.0, 0.0, 1.0, 0.0]
                            ^"[_]f32" [0.0, 0.0, 0.0, 1.0]])

(test "multidimensional arrays"
  ;; Access the 2D array by indexing the outer array, and then the inner array.
  (assert (= (aget mat4x4 1 1) 1.0))

  ;; Here we iterate with for loops.
  (for [[row, row_index] mat4x4]
    (for [[cell, column_index] row]
      (when (= row_index column_index)
        (assert (= cell 1.0))))))

;; == test volatile
(const assert (.. (@import "std") -debug -assert))

(test "volatile"
  (const mmio_ptr (@intToPtr (zig* "*volatile u8") 0x12345678))
  (assert (= (@TypeOf mmio_ptr) (zig* "*volatile u8"))))

;; == test pointer casting
(const std (@import "std"))
(const assert std.debug.assert)

(test "pointer casting"
    (const ^{:align "@alignOf(u32)"} bytes ^"[_]u8" [0x12, 0x12, 0x12, 0x12])
    (const u32_ptr (@ptrCast (zig* "*const u32") (& bytes)))
    (assert (= u32_ptr.* 0x12121212))

    ;; Even this example is contrived - there are better ways to do the above than
    ;; pointer casting. For example, using a slice narrowing cast:
    (const u32_value (-> (.bytesAsSlice std.mem u32 (slice bytes 0))
                         (aget 0)))
    (assert (= u32_value 0x12121212))

    ;; And even another way, the most straightforward way to do it:
    (assert (= (@bitCast u32 bytes) 0x12121212)))

(test "pointer child type"
    ;; pointer types have a `child` field which tells you the type they point to.
    (assert (= (.-Child (zig* "(*u32)")) u32)))
