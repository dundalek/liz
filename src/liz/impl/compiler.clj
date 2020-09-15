(ns liz.impl.compiler
  (:refer-clojure :exclude [compile])
  (:require [liz.impl.analyzer :as ana]
            [liz.impl.reader :as reader]
            [clojure.tools.analyzer.env :as env]
            [clojure.java.shell :refer [sh]]
            [liz.impl.emitter :as emitter]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(defn compile [forms]
  (env/ensure (ana/global-env)
    (doseq [form forms]
      ;; TODO One try/catch for analyzer and one for emitter
      (try
        (let [ast (ana/analyze form)]
          #_(binding [*print-meta* true
                      *out* *err*]
              (pprint ast))
          (emitter/emit ast))
        (catch Exception e
          (let [{:keys [node]} (ex-data e)]
            (cond
              node
              (do (binding [*print-meta* true]
                    (pprint node))
                  (println (ex-message e)))

              :else (binding [*out* *err*]
                      (println "Unexpected error" e)))))))))

(defn compile-file [file-in out-dir]
  (let [file-out (str out-dir "/" (str/replace file-in #"\.[^.]+$" ".zig"))]
    (with-open [writer (io/writer file-out)]
      (binding [*out* writer]
        (-> (slurp file-in)
            (reader/read-all-string)
            (compile))))

    #_(with-open [rdr (rt/indexing-push-back-reader
                        (io/reader file-in))
                  writer (io/writer file-out)]
        (binding [*out* writer]
          (compile (read-all rdr))))

    ;; TODO detect if zig is present
    (let [{:keys [exit err]} (sh "zig" "fmt" file-out)]
      (when-not (zero? exit)
        (println (str "zig fmt error for " file-out ":"))
        (println err)))))

(comment
  (let [form (first (reader/read-all-string "(const assert (.. (@import \"std\") -debug -assert))"))
        ast (env/ensure (ana/global-env)
              (ana/analyze form))]
    (binding [*print-meta* true]
              ;*print-level* 5]
      (pprint ast))
   (emitter/emit ast)))
