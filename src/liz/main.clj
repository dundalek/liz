(set! *warn-on-reflection* true)
(ns liz.main
  (:gen-class)
  (:refer-clojure :exclude [macroexpand-1 compile])
  (:require #_[clojure.tools.reader :as reader]
            #_[clojure.tools.reader.reader-types :as rt]
            [clojure.tools.analyzer :as ana]
            [clojure.tools.analyzer.passes :as ana.passes]
            [clojure.tools.analyzer.passes
             [source-info :refer [source-info]]]
            [clojure.tools.analyzer.env :as env]
            [clojure.tools.cli :as cli]
            [clojure.java.io :as io]
            [clojure.tools.analyzer.jvm :as ana.jvm]
            [clojure.tools.analyzer.passes.jvm.emit-form :refer [emit-form]]
            [edamame.core :as edamame]
            [liz.emitter :refer [emit]]
            [liz.lang :refer [binary-ops]]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [clojure.java.shell :refer [sh]]))

(defmacro liz-defn [& body]
  (cons 'fn body))

(defn build-ns-map []
  (let [mappings {'-> #'clojure.core/->
                  '->> #'clojure.core/->>
                  '= #'clojure.core/=
                  '.. #'clojure.core/..
                  'aset #'clojure.core/aset
                  'cond #'clojure.core/cond
                  'defn #'liz-defn
                  'deref #'clojure.core/deref
                  'fn #'clojure.core/fn
                  'int #'clojure.core/int
                  'when #'clojure.core/when}]
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
    (cons 'vari (rest form))
    (ana.jvm/macroexpand-1 form env)))

(defn classify-invoke
  ""
  {:pass-info {:walk :post :depends #{} :after #{#'source-info}}}
  [{:keys [op target form tag env class] :as ast}]
  (if-not (= op :invoke)
    ast
    (let [the-fn (:fn ast)]
      (if (#{'async 'await 'resume 'return 'defer 'errdefer 'continue 'break 'unreachable 'usingnamespace} (:form the-fn))
        (assoc ast :op :statement)
        ast))))

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
     #_ ana.jvm/run-passes
     (-> (ana/analyze form env)
         (run-passes)))))

#_(defn read-all
    ([rdr] (read-all {:eof (Object.)} rdr))
    ([{:keys [eof] :as opts} rdr]
     (let [form (reader/read opts rdr)]
       (when-not (identical? form eof)
         (cons form (lazy-seq (read-all opts rdr)))))))

#_(defn read-all-string [s]
    (read-all (rt/indexing-push-back-reader
                (rt/string-reader s))))

(defn read-all-string [s]
  (edamame/parse-string-all s {:deref true}))

(defn compile [forms]
  (env/ensure (global-env)
    (doseq [form forms]
      ;; TODO One try/catch for analyzer and one for emitter
      (try
        (let [ast (analyze form)]
          #_(binding [*print-meta* true
                      *out* *err*]
              (pprint ast))
          (emit ast))
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
            (read-all-string)
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
                   (compile-file file-in out-dir))
                 (shutdown-agents)))))

(comment
  (let [form (reader/read-string
               "(struct ^:var ^i32 x 1234)")
        ast (env/ensure (global-env)
              (analyze form (ana/empty-env)))]

    (binding [*print-meta* true]
              ;*print-level* 5]
      (pprint ast))

   (emit ast)))
