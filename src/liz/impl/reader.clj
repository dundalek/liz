(ns liz.impl.reader
  (:require [edamame.core :as edamame]
            #_[clojure.tools.reader :as reader]
            #_[clojure.tools.reader.reader-types :as rt]))

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
  (edamame/parse-string-all s {:deref true
                               :row-key :line
                               :col-key :column
                               :end-row-key :end-line
                               :end-col-key :end-column}))
