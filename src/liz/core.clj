(ns liz.core
  (:refer-clojure :exclude [bit-and bit-or bit-shift-left bit-shift-right bit-flip bit-set bit-test dec defn inc mod zero? pos? neg? even? odd? rem]))

(defmacro defn [& body]
  (cons 'fn body))

(defmacro bit-and [& body]
  (cons '& body))

(defmacro bit-or [& body]
  (cons '| body))

(defmacro bit-shift-left [& body]
  (cons '<< body))

(defmacro bit-shift-right [& body]
  (cons '>> body))

(defmacro bit-flip [x n]
  (list 'bit-xor x (list '<< 1 n)))

;; TODO negation seems to require @as casting
; (defmacro bit-clear [x n]
;   (list 'bit-and x (list 'bit-not (list '<< 1 n))))

(defmacro bit-set [x n]
  (list 'bit-or x (list '<< 1 n)))

(defmacro bit-test [x n]
  (list 'not= (list 'bit-and x (list '<< 1 n)) 0))

(defmacro while-some [bindings & body]
  ; (vector? bindings) "a vector for its binding"
  ; (= 2 (count bindings)) "exactly 2 forms in binding vector"
  (list 'while (bindings 1)
        (cons 'bind
              (cons (bindings 0) body))))

(defmacro zero? [num]
  (list '= num 0))

(defmacro pos? [num]
  (list '< 0 num))

(defmacro neg? [num]
  (list '< num 0))

(defmacro even? [n]
  (list '= 0 (list 'mod n 2)))

(defmacro odd? [n]
  (list '= 1 (list 'mod n 2)))

(defmacro mod [num div]
  (list '% num div))

(defmacro rem [num div]
  (list (symbol "@rem") num div))

(defmacro inc [x]
  (list '+ x 1))

(defmacro inc! [x]
  (list '+= x 1))

(defmacro dec [x]
  (list '- x 1))

(defmacro dec! [n]
  (list '-= n 1))
