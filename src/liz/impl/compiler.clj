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

(defn- print-error [file-name form e]
  (let [{:keys [node]} (ex-data e)
        line (or (-> e ex-data :line) (-> form meta :line))
        column (or (-> e ex-data :column) (-> form meta :column))]
    (print (str file-name ":" line ":" column ": error: "))
    (cond
      (instance? clojure.lang.ArityException e) (println (ex-message e))
      node (do
             (println (ex-message e))
             (println "Please open an issue and include the source code that caused this: https://github.com/dundalek/liz/issues/new"))
      (ex-data e) (println (ex-message e))
      :else (println e))))

(defn- compile-form [form]
  (let [ast (ana/analyze form)]
    (emitter/emit ast)))

(defn- compile
  ([forms] (compile "NO_SOURCE_PATH" forms))
  ([file-in forms]
   (let [!success (atom true)]
     (env/ensure
      (ana/global-env)
      (doseq [form forms]
        (try
          (compile-form form)
          (catch Exception e
            (reset! !success false)
            (binding [*out* *err*]
              (print-error file-in form e))))))
     @!success)))

(defn compile-file [file-in out-dir]
  (let [!success (atom true)]
    (try
      (let [forms (-> (slurp file-in) (reader/read-all-string))
            file-out (str out-dir "/" (str/replace file-in #"\.[^.]+$" ".zig"))
            parent (-> (io/file file-out) (.getParentFile))
            file-out (str/replace file-out #"^\./" "")]
        (when-not (.isDirectory parent)
          (.mkdirs parent))
        (with-open [writer (io/writer file-out)]
          (binding [*out* writer]
            (when-not (compile file-in forms)
              (reset! !success false))))
        (when @!success
          (let [{:keys [exit err]} (sh "zig" "fmt" file-out)]
            (when-not (zero? exit)
              (binding [*out* *err*]
                (reset! !success false)
                ;; No need to print extra info since
                ;; Zig's error output already includes the file name
                (print err)
                (flush))))))
      (catch Exception e
        (reset! !success false)
        (binding [*out* *err*]
          (when-not (reader/print-error file-in e)
            (println file-in ": error: " e)))))
    @!success))

(defn compile-string [s]
  (let [!success (atom true)]
    (try
      (let [forms (reader/read-all-string s)
            out (with-out-str
                  (when-not (compile forms)
                    (reset! !success false)))]
        (when @!success
          (let [{:keys [exit err out]} (sh "zig" "fmt" "--stdin" :in out)]
            (if (zero? exit)
              (print out)
              (binding [*out* *err*]
                (reset! !success false)
                (print err)
                (flush))))))
      (catch Exception e
        (reset! !success false)
        (binding [*out* *err*]
          (when-not (reader/print-error "NO_SOURCE_PATH" e)
            (println "error: " e)))))
    @!success))

(comment
  (let [form (first (reader/read-all-string "(defn a 0)"))
        _ (binding [*print-meta* true]
                    ;*print-level* 5]
            (pprint form))
        ast (env/ensure (ana/global-env)
                        (ana/analyze form))]
    (binding [*print-meta* true]
              ;*print-level* 5]
      (pprint ast))
    (emitter/emit ast)))
