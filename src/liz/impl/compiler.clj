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

(defn compile
  ([forms] (compile forms "NO_SOURCE_PATH"))
  ([forms file-name]
   (env/ensure
    (ana/global-env)
    (doseq [form forms]
      ;; TODO One try/catch for analyzer and one for emitter
      (try
        (let [ast (ana/analyze form)]
          (emitter/emit ast))
        (catch Exception e
          (let [{:keys [node]} (ex-data e)
                line (or (-> e ex-data :line) (-> form meta :line))
                column (or (-> e ex-data :column) (-> form meta :column))]
            (binding [*out* *err*]
              (print (str file-name ":" line ":" column ": error: "))
              (cond
                node (do
                       (println (ex-message e))
                       (println "Please open an issue and include the source code that caused this: https://github.com/dundalek/liz/issues/new"))
                (ex-data e) (println (ex-message e))
                :else (println e))))))))))

(defn compile-file [file-in out-dir]
  (let [file-out (str out-dir "/" (str/replace file-in #"\.[^.]+$" ".zig"))
        parent (-> (io/file file-out) (.getParentFile))]
    (when-not (.isDirectory parent)
      (.mkdirs parent))
    (with-open [writer (io/writer file-out)]
      (binding [*out* writer]
        (-> (slurp file-in)
            (reader/read-all-string)
            (compile file-in))))

    #_(with-open [rdr (rt/indexing-push-back-reader
                       (io/reader file-in))
                  writer (io/writer file-out)]
        (binding [*out* writer]
          (compile (read-all rdr))))

    ;; TODO detect if zig is present
    (let [{:keys [exit err]} (sh "zig" "fmt" file-out)]
      (when-not (zero? exit)
        (binding [*out* *err*]
          (println (str "zig fmt error for " file-out ":"))
          (println err))))))

(defn compile-string [s]
  (let [out (with-out-str
              (-> s
                  (reader/read-all-string)
                  (compile)))]
    ;; TODO detect if zig is present
    (let [{:keys [exit err out]} (sh "zig" "fmt" "--stdin" :in out)]
      (if (zero? exit)
        out
        (binding [*out* *err*]
          (println (str "zig fmt error:"))
          (println err))))))

(comment
  (let [form (first (reader/read-all-string "(= ('when 1) 3)"))
        ast (env/ensure (ana/global-env)
                        (ana/analyze form))]
    (binding [*print-meta* true]
              ;*print-level* 5]
      (pprint form)
      (pprint ast))
    (emitter/emit ast)))
