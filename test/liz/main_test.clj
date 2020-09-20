(ns liz.main-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [liz.impl.reader :as reader]
            [liz.impl.compiler :as compiler]
            [clojure.java.io :as io]
            [clojure.java.shell :refer [sh]])
  (:import (java.io File)))

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

(defn run-test-case [{:keys [name action content]} result]
  (testing name
    (with-temp-file
      (fn [out-file]
        (with-open [writer (io/writer out-file)]
          (binding [*out* writer]
            (-> (reader/read-all-string content)
                (compiler/compile))))
        (is (= {:exit 0 :out "" :err (str out-file "\n")}
               (sh "zig" "fmt" out-file)))
        (let [{:keys [exit out err]} (sh "zig" action out-file)]
          (is (= name (:name result)))
          (is (= exit 0))
          (is (= (str/trim (str out "\n" err))
                 (str/trim (:content result))))

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
           (count results)))
    (doseq [[{:keys [name] :as test-case} result] tests]
      (run-test-case test-case result))))

;; Clearing Zig cache because it grows indefinately in watch mode due to random file names and eventully exhausts disk space.
(defn clear-cache []
  (sh "rm" "-rf" "zig-cache"))

(deftest docs-samples
  (clear-cache)
  (run-test-cases
    (read-tests "test/resources/docs-samples.cljc")
    (read-tests "test/resources/docs-samples-output.txt")))

(deftest features
  (clear-cache)
  (run-test-cases
    (read-tests "test/resources/features.cljc")
    (read-tests "test/resources/features-output.txt")))

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
