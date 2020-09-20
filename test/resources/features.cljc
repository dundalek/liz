;; == test using special form names
(const std (@import "std"))
(const assert std.debug.assert)

(defn ^usize when [^usize a]
  (return (+ a 2)))

(test "using special form names"
  (assert (= ('when 1) 3)))

;; == test conditionals tests
(const std (@import "std"))
(const assert std.debug.assert)

(test "if-not"
  (if-not false
    (assert true)
    (unreachable)))

(test "when with multiple forms"
  (var ^usize i 0)
  (when true
    (+= i 1)
    (+= i 2))
  (assert (= i 3)))

(test "when-not"
  (var ^usize i 0)
  (when-not false
    (+= i 1)
    (+= i 2))
  (assert (= i 3)))

;; == test Operators
(const std (@import "std"))
(const assert std.debug.assert)

(test "Addition"
  (assert (= 7 (+ 2 5)))

  (var ^i32 a 2)
  (+= a 5)
  (assert (= 7 a)))

(test "Wrapping Addition"
  (assert (= 0 (+% (@as u32 (.maxInt std.math u32)) 1)))

  (var ^u32 a (.maxInt std.math u32))
  (+%= a 1)
  (assert (= 0 a)))

(test "Subtraction"
  (assert (= -3 (- 2 5)))

  (var ^i32 a 2)
  (-= a 5)
  (assert (= -3 a)))

(test "Wrapping Subtraction"
  (assert (= (.maxInt std.math u32) (-% (@as u32 0) 1)))

  (var ^u32 a (@as u32 0))
  (-%= a 1)
  (assert (= (.maxInt std.math u32) a)))

(test "Negation"
  (assert (= -1 (- 0 1))))

(test "Wrapping Negation"
  (assert (= (.minInt std.math i32)
             (-% (@as i32 (.minInt std.math i32))))))

(test "Multiplication"
  (assert (= 10 (* 2 5))))

(test "Wrapping Multiplication"
  (assert (= 144 (*% (@as u8 200) 2))))

(test "Division"
  (assert (= 2 (/ 10 5))))

(test "Remainder Division"
  (assert (= 1 (% 10 3))))

(test "Bit Shift Left"
  (assert (= 256 (<< 1 8))))

(test "Bit Shift Right"
  (assert (= 5 (>> 10 1))))

(test "Bitwise AND"
  (assert (= 2r001 (& 2r011 2r101))))

(test "Bitwise OR"
  (assert (= 2r110 (| 2r010 2r100))))

(test "Bitwise XOR"
  (assert (= 2r110 (bit-xor 2r011 2r101))))

(test "Bitwise NOT"
  (assert (= (bit-not (@as u8 2r10101111)) 2r01010000)))

(test "Optionals unwrapping"
  (const ^?u32 value nil)
  (const unwrapped (orelse value 1234))
  (assert (= unwrapped 1234)))

(test "Optionals unwrapping shorthand"
  (const ^?u32 value 5678)
  (assert (= value.? 5678)))
  ;; TODO: maybe support (assert (= (? value) 5678))

(test "Error Unions unwrapping"
  (const ^anyerror!u32 value error.Broken)
  (const unwrapped (catch value 1234))
  (assert (= unwrapped 1234)))

(test "Boolean AND"
  (assert (= false (and false true))))

(test "Boolean OR"
  (assert (= true (or false true))))

(test "Boolea NOT"
  (assert (= true (not false))))

(test "Equals"
  (assert (= true (= 1 1))))

(test "Optionals nil equality"
  (const ^?u32 value nil)
  (assert (= nil value)))

(test "Not equal"
  (assert (= false (!= 1 1)))
  (assert (= false (not= 1 1))))

(test "Greater than"
  (assert (= true (> 2 1)))
  (assert (= false (> 2 2))))

(test "Greater or eaqual than"
  (assert (= true (>= 2 1)))
  (assert (= true (>= 2 2))))

(test "Less than"
  (assert (= true (< 1 2)))
  (assert (= false (< 2 2))))

(test "Less or equal than"
  (assert (= true (<= 1 2)))
  (assert (= true (<= 2 2))))

(test "Array concatenation"
  (const array1 ^"[_]u32" [1 2])
  (const array2 ^"[_]u32" [3 4])
  (const together (++ array1 array2))
  (assert (.eql std.mem u32 (& together) (& ^"[_]u32" [1 2 3 4]))))

(test "Array multiplication"
  (const pattern (** "ab" 3))
  (assert (.eql std.mem u8 pattern "ababab")))

(test "Pointer dereference"
  (const ^u32 x 1234)
  (const ptr (& x))
  (assert (= ptr.* 1234)))

(test "Address of"
  (const ^u32 x 1234)
  (const ptr (& x))
  (assert (= ptr.* 1234)))

;; TODO codegen looks good, perhaps a new feature not available in Zig 0.6.0
; (test "Merging Error Sets"
;   (const A (error One))
;   (const B (error Two))
;   (assert (= (error One Two) (|| A B))))
;; == test Comment form
(const std (@import "std"))
(const assert std.debug.assert)

(test "clj comment"
  (var ^i32 a 0)
  (comment
    (+= a 1)
    (+= a 2))
  (assert (= a 0)))
;; == test Bit operators aliases
(const std (@import "std"))
(const assert std.debug.assert)

(test "bit-and"
  (assert (= 2r001 (bit-and 2r011 2r101))))

(test "bit-or"
  (assert (= 2r110 (bit-or 2r010 2r100))))

(test "bit-shift-left"
  (assert (= 256 (bit-shift-left 1 8))))

(test "bit-shift-right"
  (assert (= 5 (bit-shift-right 10 1))))
