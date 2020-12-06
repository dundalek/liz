(ns liz.impl.lang)

;; set! .. =

;; TODO ^=
;; TODO /=
;; !x -x -%x ~x &x ?x
;; x{} x.* x.?

(def binary-ops
  (conj '#{! * / % ** *% ||
           + - ++ +% -%
           << >>
           & | ; ^
           == != < > <= >=
           and
           or
           orelse catch
           *= %= += -= <<= >>= &= |= +%= -%=}
        (symbol "^")
        (symbol "~")))
