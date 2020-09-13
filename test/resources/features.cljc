;; == test using special form names
(const std (@import "std"))
(const assert std.debug.assert)

(defn ^usize when [^usize a]
  (return (+ a 2)))

(test "using special form names"
  (assert (= ('when 1) 3)))
