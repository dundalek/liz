(ns liz.main-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [liz.main :as liz]
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

;(deftest compile-forms
;  (is (= "pub extern \"c\" fn printf(format: [*:0]const u8, ...) c_int;"
;         (with-out-str
;           (-> (liz/read-all-string "(fn ^:pub ^c_int printf [^\"[*:0]const u8\" format ...])")
;               (liz/compile))))))

(deftest docs-samples
  (let [samples (read-tests "test/resources/docs-samples.cljc")
        results (read-tests "test/resources/docs-samples-output.txt")
        tests (map vector samples results)]
    (is (= (count samples)
           (count results)))
    (doseq [[{:keys [name action content]} result] tests]
      (testing name
        (with-temp-file
          (fn [out-file]
            (with-open [writer (io/writer out-file)]
              (binding [*out* writer]
                (-> (liz/read-all-string content)
                    (liz/compile))))
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
                (println err)))))))))
