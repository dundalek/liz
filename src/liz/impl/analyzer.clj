(ns liz.impl.analyzer
  (:refer-clojure :exclude [macroexpand-1])
  (:require [clojure.tools.analyzer :as ana]
            [clojure.tools.analyzer.env :as env]
            [clojure.tools.analyzer.passes :as ana.passes]
            [clojure.tools.analyzer.passes
             [source-info :refer [source-info]]]
            [liz.core :as core]
            [liz.impl.passes
             [classify-invoke :as classify-invoke]]
            [clojure.tools.analyzer.jvm :as ana.jvm]
            [clojure.tools.analyzer.utils :as ana.utils]))

;; Replacing (ana.jvm/build-ns-map)
(defn build-ns-map []
  (let [mappings {'-> #'clojure.core/->
                  '->> #'clojure.core/->>
                  '= #'clojure.core/=
                  '.. #'clojure.core/..
                  'aset #'core/aset
                  'bit-and #'core/bit-and
                  ; 'bit-clear #'core/bit-clear
                  'bit-flip #'core/bit-flip
                  'bit-not #'core/bit-not
                  'bit-or #'core/bit-or
                  'bit-set #'core/bit-set
                  'bit-shift-left #'core/bit-shift-left
                  'bit-shift-right #'core/bit-shift-right
                  'bit-test #'core/bit-test
                  'bit-xor #'core/bit-xor
                  'cond #'clojure.core/cond
                  'dec #'core/dec
                  'dec! #'core/dec!
                  'defn #'core/defn
                  'deref #'clojure.core/deref
                  'even? #'core/even?
                  'fn #'clojure.core/fn
                  'if-not #'clojure.core/if-not
                  'inc #'core/inc
                  'inc! #'core/inc!
                  'int #'clojure.core/int
                  'mod #'core/mod
                  'neg? #'core/neg?
                  'not #'core/not
                  'not= #'core/not=
                  'odd? #'core/odd?
                  'pos? #'core/pos?
                  'rem #'core/rem
                  'when #'clojure.core/when
                  'when-not #'core/when-not
                  'while-some #'core/while-some
                  'zero? #'core/zero?}]
    {'clojure.core
     {:mappings mappings
      :aliases {}
      :ns 'clojure.core}
     'user
     {:mappings mappings
      :aliases {}
      :ns 'user}}))

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
