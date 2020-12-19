(ns liz.core
  (:refer-clojure :exclude [bit-and bit-or bit-shift-left bit-shift-right bit-flip bit-set bit-test bit-xor bit-not
                            dec defn inc mod zero? pos? neg? even? odd? rem aset not= not when-not if-some when-some]))

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

(defmacro bit-xor [& body]
  (cons (symbol "^") body))

(defmacro bit-not [x]
  (list (symbol "~") x))

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

;; Customized implementation because code expanded using clojure.core/when-not
;; can result in Zig error: `expression value is ignored`.
(defmacro when-not [test & body]
  (list 'if
        (list 'not test)
        (cons 'do body)))

(defmacro if-some
  ([bindings then]
   (list 'if-some bindings then nil))
  ([bindings then else]
   (list 'if (bindings 1)
         (list 'bind (bindings 0) then)
         else)))

(defmacro when-some [bindings & body]
  (list 'if-some bindings
        (cons 'do body)))

(defmacro not [x]
  (list '! x))

(defmacro not= [x y]
  (list '!= x y))

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

(defmacro aset [& args]
  (when (< (count args) 3)
    (throw (ex-info (str "(aset) was given " (count args) " arguments, but expects at least 3") {})))
  (list 'set!
        (cons 'aget (butlast args))
        (last args)))
