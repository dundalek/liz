(ns liz.impl.lang)

;; set! .. =
;; not !

;; TODO ^
;; TODO ^=
;; TODO /=
;; !x -x -%x ~x &x ?x
;; x{} x.* x.?

(def binary-ops
  '#{* / % ** *% ||
     + - ++ +% -%
     << >>
     & | ; ^
     == != < > <= >=
     and
     or
     orelse catch
     *= %= += -= <<= >>= &= |= +%= -%=})
