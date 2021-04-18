(set! *warn-on-reflection* true)
(ns liz.main
  (:gen-class)
  (:require [clojure.tools.cli :as cli]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [liz.impl.compiler :as compiler]))

(def version (str/trim (slurp (io/resource "LIZ_VERSION"))))

(defn usage [options-summary]
  (->> ["Usage: liz [options] [file...]"
        ""
        "Options:"
        options-summary]
       (str/join \newline)))

(defn error-msg [errors]
  (->> errors
       (cons "The following errors occurred while parsing your command:\n\n")
       (str/join \newline)))

(def cli-options
  [["-h" "--help"]
   [nil "--out-dir DIR" "Directory where to output compiled files. Defaults to current directory."]
   [nil "--version"]])

(defn process-args [args]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-options)]
    (cond
      (:version options)
      {:action :exit
       :options {:message version
                 :ok? true}}

      errors
      {:action :exit
       :options {:message (error-msg errors)}}

      (pos? (count arguments))
      {:action :compile
       :options {:out-dir (:out-dir options)
                 :files arguments}}

      :else
      {:action :exit
       :options {:message (usage summary)
                 :ok? true}})))

(defn exit-message [{:keys [message ok?]}]
  (println message)
  (System/exit (if ok? 0 1)))

(defn compile-stdin []
  (let [success (compiler/compile-string (slurp *in*))]
    (flush)
    (shutdown-agents)
    (System/exit (if success 0 1))))

(defn compile-files [files out-dir]
  (let [!success (atom true)]
    ;; Process all files regardless of errors, compile-file captures all errors and reports them to stderr.
    ;; If compilation for any liz file fails it is likely that zig compilation will also fail
    ;; and its errors would just distract from liz errors. Therefore we exit with failing code,
    ;; so that usage like `liz src/*.liz && zig build` will not continue with zig compilation.
    (doseq [file-in files]
      (when-not (compiler/compile-file file-in out-dir)
        (reset! !success false)))
    (shutdown-agents)
    (System/exit (if @!success 0 1))))

(defn -main [& args]
  (let [{:keys [action options]} (process-args args)]
    (case action
      :exit (exit-message options)
      :compile (let [{:keys [files out-dir]} options
                     out-dir (or out-dir ".")]
                 (if (and (= (count files) 1)
                          (= (first files) "-"))
                   (compile-stdin)
                   (compile-files files out-dir))))))
