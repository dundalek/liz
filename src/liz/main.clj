(ns liz.main
  (:refer-clojure :exclude [macroexpand-1 compile])
  (:require [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as rt]
            [clojure.tools.analyzer :as ana]
            [clojure.tools.analyzer.env :as env]
            [clojure.java.io :as io]
            [clojure.tools.analyzer.jvm :as ana.jvm]
            [clojure.tools.analyzer.passes.jvm.emit-form :refer [emit-form]]
            [liz.emitter :refer [emit]]
            [liz.lang :refer [binary-ops]]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.analyzer.utils :as ana.utils]
            [clojure.string :as str]
            [clojure.java.shell :refer [sh]]))

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

(def custom-forms (into binary-ops #{'while 'for 'assert 'case}))

(defn macroexpand-1 [form env]
  (if (and (list? form)
           (custom-forms (first form)))
    form
    (ana.jvm/macroexpand-1 form env)))

(defn analyze
  ([form] (analyze form (ana/empty-env)))
  ([form env]
   (binding [ana/macroexpand-1 macroexpand-1
             ana/create-var ana.jvm/create-var
             ana/parse ana.jvm/parse
             ana/var? var?]
     #_(ana.jvm/run-passes (ana/analyze form env))
     (ana/analyze form env))))

(defn read-all
  ([rdr] (read-all {:eof (Object.)} rdr))
  ([{:keys [eof] :as opts} rdr]
   (let [form (reader/read opts rdr)]
     (when-not (identical? form eof)
       (cons form (lazy-seq (read-all opts rdr)))))))

(defn read-all-string [s]
  (read-all (rt/indexing-push-back-reader
              (rt/string-reader s))))

(defn compile [forms]
  (env/ensure (global-env)
    (doseq [form forms]
      ;; TODO One try/catch for analyzer and one for emitter
      (try
        (-> (analyze form)
            (emit))
        (catch Exception e
          (let [{:keys [node]} (ex-data e)]
            (cond
              node
              (do (binding [*print-meta* true]
                    (pprint node))
                  (println (ex-message e)))

              (re-find #"Wrong number of args to var" (ex-message e))
              (binding [*out* *err*]
                (println e)
                (println "(var name value) conflicts with clojure, use (vari name value) as a workaround for now"))

              :else (binding [*out* *err*]
                      (println "Unexpected error" e)))))))))

(defn -main [& filenames]
  (doseq [file-in filenames]
    (let [file-out (str/replace file-in #"\.[^.]+$" ".zig")]
      (with-open [rdr (rt/indexing-push-back-reader
                        (io/reader file-in))
                  writer (io/writer file-out)]
        (binding [*out* writer]
          (compile (read-all rdr))))
      ;; TODO detect if zig is present
      (let [{:keys [exit err]} (sh "zig" "fmt" file-out)]
        (when-not (zero? exit)
          (println (str "zig fmt error for " file-out ":"))
          (println err)))))
  (shutdown-agents))

(comment
  (let [form (reader/read-string
               "(struct ^:var ^i32 x 1234)")
        ast (env/ensure (global-env)
              (analyze form (ana/empty-env)))]

    (binding [*print-meta* true]
              ;*print-level* 5]
      (pprint ast))

   (emit ast)))
