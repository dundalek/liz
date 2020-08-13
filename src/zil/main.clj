(ns zil.main
  (:refer-clojure :exclude [macroexpand-1])
  (:require [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as rt]
            [clojure.tools.analyzer :as ana]
            [clojure.tools.analyzer.env :as env]
            [clojure.java.io :as io]
            [clojure.tools.analyzer.jvm :as ana.jvm]
            [clojure.tools.analyzer.passes.jvm.emit-form :refer [emit-form]]
            [zil.emitter :refer [emit]]
            [zil.lang :refer [binary-ops]]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.analyzer.utils :as ana.utils]))

(defn build-ns-map []
  (-> (ana.jvm/build-ns-map)
      (update-in ['clojure.core :mappings] dissoc 'while)
      (update-in ['user :mappings] dissoc 'while)))

(defn global-env []
  (atom {:namespaces (build-ns-map)
         :update-ns-map! (fn update-ns-map! []
                           (swap! env/*env* assoc-in [:namespaces] (build-ns-map)))}))
;;
;(-> (get (:namespaces (deref (make-global-env)))
;         'clojure.core)
;    (:mappings)
;    (get '<)
;    meta)

;(env/with-env (global-env)
;  (ana.jvm/macroexpand-1 (reader/read-string "(while true (println i) (+= i 1))")))
;
;(-> (global-env) deref :namespaces (get 'user ) :mappings (get 'when))


;(env/ensure (global-env)
;  (ana.utils/resolve-sym 'while (ana/empty-env)))
;  ;(ana.utils/resolve-ns nil (ana/empty-env)))


(def global-env ana.jvm/global-env)

(def custom-forms (into binary-ops #{'while 'for}))

(defn macroexpand-1 [form env]
  (if (and (list? form)
           (custom-forms (first form)))
    form
    (ana.jvm/macroexpand-1 form env)))

(defn analyze+emit
  ([form] (analyze+emit form (ana/empty-env)))
  ([form env]
   (let [ast (binding [ana/macroexpand-1 macroexpand-1
                       ana/create-var ana.jvm/create-var
                       ana/parse ana.jvm/parse
                       ana/var? var?]
                    #_(ana.jvm/run-passes (ana/analyze form env))
                  (ana/analyze form env))]
     (emit ast))))

(defn -main []
  (let [rdr (rt/indexing-push-back-reader
              (io/reader "examples/imgui-dice-roller/src/main.cljc"))
        eof (Object.)
        forms  (loop [forms []]
                     (let [form (reader/read {:eof eof} rdr)]
                       (if-not (identical? form eof)
                         (do #_(println "Form:    " (type form) form)
                             ; (println "Analyzed:" (ana/analyze env form))
                             (recur (conj forms form)))
                         forms)))]
    (env/ensure (global-env)
      (doseq [form forms]
        ;(println "Form:" form)
        ;(println "Emit:")
        (analyze+emit form)))))
        ;(println "\n")))))


(comment
  (let [form (reader/read-string "(const dice ^\"[_]Dice\" (array 1 2))")
        ast (binding [ana/macroexpand-1 macroexpand-1
                      ana/create-var ana.jvm/create-var
                      ana/parse ana.jvm/parse
                      ana/var? var?]
                   #_(ana.jvm/run-passes (ana/analyze form env))
                   (env/ensure (global-env)
                     (ana/analyze form (ana/empty-env))))]

       (binding [*print-meta* true]
                 ;*print-level* 5]
         (pprint ast))

      (emit ast)))
