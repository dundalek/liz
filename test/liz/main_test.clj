(ns liz.main-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [liz.impl.reader :as reader]
            [liz.impl.compiler :as compiler]
            [clojure.java.shell :refer [sh]])
  (:import (java.io File StringWriter)))

(defn with-temp-file [cb]
  (let [file (File/createTempFile "liz-test-" ".zig")
        f (.getAbsolutePath file)
        _ (.deleteOnExit file)]
    (cb f)))

(defn parse-header [s]
  (when-let [[_ action name] (re-find #"^\s*;+\s*==\s*(\w+)\s+(.*)" s)]
    {:action action
     :name name}))

(defn read-tests [filename]
  (->> (slurp filename)
       (str/split-lines)
       (partition-by parse-header)
       (partition 2)
       (map (fn [[[header] lines]]
              (assoc (parse-header header)
                     :content (str/join "\n" lines))))))

(def compile-string
  (let [liz-bin (System/getenv "LIZ_BINARY_PATH")]
    (if (str/blank? liz-bin)
      (fn compile-string-in-memory [s]
        (with-out-str
          (compiler/compile-string s)))
      (fn compile-string-with-binary [s]
        (:out (sh liz-bin "-" :in s))))))

(defn run-test-case [{:keys [name action content]} result]
  (testing name
    (with-temp-file
      (fn [out-file]
        (->> (compile-string content)
             (spit out-file))
        (let [{:keys [exit out err]} (sh "zig" action out-file)]
          (is (= (:name result) name))
          (is (= 0 exit))
          (is (= (str/trim (:content result))
                 (str/trim (str out "\n" err))))

          (comment
            (println (str ";; == " action " " name))
            (println out)
            (println err)))))))

;(deftest compile-forms
;  (is (= "pub extern \"c\" fn printf(format: [*:0]const u8, ...) c_int;"
;         (with-out-str
;           (-> (liz/read-all-string "(fn ^:pub ^c_int printf [^\"[*:0]const u8\" format ...])")
;               (liz/compile))))))

(defn run-test-cases [cases results]
  (let [tests (map vector cases results)]
    (is (= (count cases)
           (count results))
        "number of tests matches number of test outputs")
    (doseq [[{:keys [name] :as test-case} result] tests]
      (run-test-case test-case result))))

;; Clearing Zig cache because it grows indefinately in watch mode due to random file names and eventully exhausts disk space.
(defn clear-cache []
  (sh "rm" "-rf" "zig-cache"))

(deftest docs-samples
  (clear-cache)
  (run-test-cases
   (read-tests "test/resources/docs-samples.liz")
   (read-tests "test/resources/docs-samples-output.txt")))

(deftest features
  (clear-cache)
  (run-test-cases
   (read-tests "test/resources/features.liz")
   (read-tests "test/resources/features-output.txt")))

(deftest error-reporting
  (let [out (StringWriter.)
        err (StringWriter.)
        _ (binding [*out* out
                    *err* err]
            (-> (slurp "test/resources/error-reporting-fixture.liz")
                (compiler/compile-string)))
        expected-lines (str/split-lines (slurp "test/resources/error-reporting-output.txt"))
        actual-lines (str/split-lines (str err))]
    (is (= "" (str out)))
    (is (= (count expected-lines) (count actual-lines))
        "number of reported errors matches")
    (doseq [[expected actual] (map list expected-lines actual-lines)]
      (is (= expected actual)))))

(deftest reporting-reader-errors
  (let [out (StringWriter.)
        err (StringWriter.)]
    (binding [*out* out
              *err* err]
      (compiler/compile-string "\n  )\n]"))
    (is (= "" (str out)))
    (is (= "NO_SOURCE_PATH:2:3: reader error: Unmatched delimiter: )\n" (str err)))))

(deftest test-defns
  (is (= "pub fn main() void {\n    print(\"Hello\\n\", .{});\n}\n"
         (compile-string (binding [*print-meta* true]
                           (pr-str '(defn ^void main []
                                      (print "Hello\n" []))))))
      "defn automatically adds pub modifier")

  (is (= "fn main() void {\n    print(\"Hello\\n\", .{});\n}\n"
         (compile-string (binding [*print-meta* true]
                           (pr-str '(defn- ^void main []
                                      (print "Hello\n" []))))))
      "defn- results in private declaration without pub modifier"))

(deftest test-noalias
  (is (= "pub fn readlink(noalias path: [*:0]const u8, noalias buf_ptr: [*]u8, buf_len: usize) usize;\n"
         (compile-string (binding [*print-meta* true]
                           (pr-str '(defn ^usize readlink [^:noalias ^"[*:0]const u8" path ^:noalias ^"[*]u8" buf_ptr ^usize buf_len])))))
      "noalias parameter modifier"))

(deftest test-pub-usingnamespace
  (is (= "pub usingnamespace @import(\"std\");\n"
         (compile-string "(^:pub usingnamespace (@import \"std\"))"))))

(deftest test-usingnamespace-cimport
  (with-temp-file
    (fn [out-file]
      (->> (compile-string
            "(usingnamespace
               (@cImport (do (@cInclude \"stdio.h\"))))
             (defn ^void main []
               (set! _ (printf \"Hello\\n\")))")
           (spit out-file))
      (let [{:keys [exit out err]} (sh "zig" "run" "-lc" out-file)]
        (is (= 0 exit))
        (is (= "Hello\n" out))
        (is (= "" err))))))

;;(defmacro define-test-cases [suite-name cases results]
;;  (let [tests (map vector (eval cases) (eval results))]
;;    `(do (is (= ~(count cases)
;;                ~(count results)))
;;         ~@(for [[{:keys [name] :as test-case} result] tests]
;;             `(deftest ~(symbol (str suite-name "--" (str/replace name #"\s" "--")))
;;                (run-test-case ~test-case ~result))))))
;;
;;(define-test-cases
;;  "samples"
;;  (read-tests "test/resources/docs-samples.cljc")
;;  (read-tests "test/resources/docs-samples-output.txt"))
;;
;;(define-test-cases
;;  "features"
;;  (read-tests "test/resources/features.cljc")
;;  (read-tests "test/resources/features-output.txt"))
