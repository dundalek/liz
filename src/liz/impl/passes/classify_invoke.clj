(ns liz.impl.passes.classify-invoke)

(defn classify-invoke
  [{:keys [op target form tag env class] :as ast}]
  (if-not (= op :invoke)
    ast
    (let [the-fn (:fn ast)]
      (if (#{'async 'await 'resume 'return 'defer 'errdefer 'continue 'break 'unreachable 'usingnamespace} (:form the-fn))
        (assoc ast :op :statement)
        ast))))
