(ns liz.impl.analyzer
  (:refer-clojure :exclude [macroexpand-1])
  (:require [clojure.tools.analyzer :as ana]
            [clojure.tools.analyzer.env :as env]
            [clojure.tools.analyzer.passes :as ana.passes]
            [clojure.tools.analyzer.passes
             [source-info :refer [source-info]]]
            [liz.impl.passes
             [classify-invoke :as classify-invoke]]
            [clojure.tools.analyzer.jvm :as ana.jvm]
            [clojure.tools.analyzer.utils :as ana.utils]))

(defmacro liz-defn [& body]
  (cons 'fn body))

(defmacro liz-bit-and [& body]
  (cons '& body))

(defmacro liz-bit-or [& body]
  (cons '| body))

(defmacro liz-bit-shift-left [& body]
  (cons '<< body))

(defmacro liz-bit-shift-right [& body]
  (cons '>> body))

(defmacro liz-bit-flip [x n]
  (list 'bit-xor x (list '<< 1 n)))

;; TODO negation seems to require @as casting
; (defmacro liz-bit-clear [x n]
;   (list 'bit-and x (list 'bit-not (list '<< 1 n))))

(defmacro liz-bit-set [x n]
  (list 'bit-or x (list '<< 1 n)))

(defmacro liz-bit-test [x n]
  (list 'not= (list 'bit-and x (list '<< 1 n)) 0))

(defn build-ns-map []
  (let [mappings {'-> #'clojure.core/->
                  '->> #'clojure.core/->>
                  '= #'clojure.core/=
                  '.. #'clojure.core/..
                  'aset #'clojure.core/aset
                  'bit-and #'liz-bit-and
                  ; 'bit-clear #'liz-bit-clear
                  'bit-flip #'liz-bit-flip
                  'bit-or #'liz-bit-or
                  'bit-set #'liz-bit-set
                  'bit-shift-left #'liz-bit-shift-left
                  'bit-shift-right #'liz-bit-shift-right
                  'bit-test #'liz-bit-test
                  'cond #'clojure.core/cond
                  'defn #'liz-defn
                  'deref #'clojure.core/deref
                  'fn #'clojure.core/fn
                  'if-not #'clojure.core/if-not
                  'int #'clojure.core/int
                  'when #'clojure.core/when
                  'when-not #'clojure.core/when-not}]
    {'clojure.core
     {:mappings mappings
      :aliases {}
      :ns 'clojure.core}
     'user
     {:mappings mappings
      :aliases {}
      :ns 'user}})
  #_(-> (ana.jvm/build-ns-map)
        (update-in ['clojure.core :mappings] dissoc 'while)
        (update-in ['user :mappings] dissoc 'while)))

(defn global-env []
  (atom {:namespaces (build-ns-map)
         :update-ns-map! (fn update-ns-map! []
                           (swap! env/*env* assoc-in [:namespaces] (build-ns-map)))}))

(defn macroexpand-1 [form env]
  (if (and (list? form) (= (first form) 'var))
    (if (not= (count form) 3)
      (throw (ex-info (str "(var) was given " (dec (count form)) " arguments, but expects 2")
                      (ana.utils/source-info (meta form))))
      (with-meta (cons 'vari (rest form)) (meta form)))
    (ana.jvm/macroexpand-1 form env)))

(def
  ^{:pass-info {:walk :post :depends #{} :after #{#'source-info}}}
  classify-invoke classify-invoke/classify-invoke)

(def default-passes
  "Set of passes that will be run by default on the AST by #'run-passes"
  #{#'source-info

    ;;#'elide-meta
    ;;#'clojure.tools.analyzer.passes.trim

    #'classify-invoke})

(def scheduled-default-passes
  (ana.passes/schedule default-passes))

(defn run-passes [ast]
  (scheduled-default-passes ast))

(defn analyze
  ([form] (analyze form (ana/empty-env)))
  ([form env]
   (binding [ana/macroexpand-1 macroexpand-1
             ana/create-var ana.jvm/create-var
             ana/parse ana/-parse
             ana/var? var?]
     #_ana.jvm/run-passes
     (-> (ana/analyze form env)
         (run-passes)))))
