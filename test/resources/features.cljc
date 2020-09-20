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
