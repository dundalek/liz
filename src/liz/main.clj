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

(defn -main [& args]
  (let [{:keys [action options]} (process-args args)]
    (case action
      :exit (exit-message options)
      :compile (let [{:keys [files out-dir]} options
                     out-dir (or out-dir ".")]
                 (doseq [file-in files]
                   (compiler/compile-file file-in out-dir))
                 (shutdown-agents)))))
