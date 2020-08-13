(ns zil.lang)

;; set! .. =

;; TODO not !
;; TODO ^
;; TODO ^=
;; TODO /=
;; !x -x -%x ~x &x ?x
;; x{} x.* x.?

(def binary-ops
  (set '[* / % ** *% ||
         + - ++ +% -%
         << >>
         & | ; ^
         == != < > <= >=
         and
         or
         orelse catch
         *= %= += -= <<= >>= &= |=]))
