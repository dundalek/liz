(ns liz.impl.reader
  (:require [edamame.core :as edamame]
            [clojure.tools.reader :as reader]
            [clojure.tools.reader.reader-types :as rt]))

(defn read-all
  ([rdr] (read-all {:eof (Object.)} rdr))
  ([{:keys [eof] :as opts} rdr]
   (let [form (reader/read opts rdr)]
     (when-not (identical? form eof)
       (cons form (lazy-seq (read-all opts rdr)))))))

(defn read-all-string [s]
  (read-all (rt/indexing-push-back-reader
              (rt/string-reader s))))

#_(defn read-all-string [s]
    (edamame/parse-string-all s {:deref true}))
