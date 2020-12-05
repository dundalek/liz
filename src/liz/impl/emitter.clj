(ns liz.impl.emitter
  (:require [clojure.pprint :refer [pprint]]
            [liz.impl.lang :refer [binary-ops]]
            [clojure.tools.analyzer.utils :as ana.utils]))

(defn assert-arg-count
  ([{:keys [args env] :as ast} cnt]
   (assert-arg-count args cnt (-> ast :fn :form) env))
  ([args cnt form info]
   (when (not= (count args) cnt)
     (throw (ex-info (str "(" form ") was given " (count args) " arguments, but expects " cnt)
                     (ana.utils/source-info info))))))

(defn assert-gte-count
  ([{:keys [args env] :as ast} cnt]
   (assert-gte-count args cnt (-> ast :fn :form) env))
  ([args cnt form info]
   (when (< (count args) cnt)
     (throw (ex-info (str "(" form ") was given " (count args) " arguments, but expects at least " cnt)
                     (ana.utils/source-info info))))))

(defn assert-lte-count
  ([{:keys [args env] :as ast} cnt]
   (assert-lte-count args cnt (-> ast :fn :form) env))
  ([args cnt form info]
   (when (> (count args) cnt)
     (throw (ex-info (str "(" form ") was given " (count args) " arguments, but expects at most " cnt)
                     (ana.utils/source-info info))))))

(defn unwrap-meta [expr]
  (cond-> expr
    (= (:op expr) :with-meta) :expr))

(defmulti -emit :op)

(defn emit [ast]
  (-emit ast))

(defn emits [a]
  (.write *out* (str a)))

(defn emits-interposed
  ([sep coll] (emits-interposed sep coll -emit))
  ([sep coll f]
   (when (seq coll)
     (f (first coll))
     (doseq [x (rest coll)]
       (emits sep)
       (f x)))))

(defn emit-operator [operator args {:keys [top-level in-statement]}]
  (when-not (or top-level in-statement)
    (emits "("))
  (if (= (count args) 1)
    ;; Unary operator - perhaps will need listing in the future
    (do
      (emits operator)
      (-emit (first args)))
    (let [sep (str " " operator " ")]
      (emits-interposed sep args)))
  (cond
    top-level (emits ";\n")
    (not in-statement) (emits ")")))

(defn emit-block [forms]
  (emits "{\n")
  (doseq [form forms]
    (-emit (assoc form :top-level true)))
  (emits "}"))

(defn maybe-emit-block
  ([forms] (maybe-emit-block forms false))
  ([forms top-level]
   (if (> (count forms) 1)
     (emit-block forms)
     (-emit (assoc (first forms) :top-level top-level
                   :in-statement true)))))

(defn emit-for [args]
  ;(pprint args)
  (emits "for (")
  (let [[binding target] (:items (unwrap-meta (first args)))
        binding (unwrap-meta binding)]
    (-emit target)
    (emits ") |")
    (if (= (:op binding) :vector)
      (emits-interposed ", " (:items binding))
      (-emit binding)))
  (emits "| ")
  (emit-block (rest args)))

(defn emit-statement [{f :fn :keys [args top-level]}]
  (emits (:form f))
  (when (seq args)
    (emits " "))
  (emits-interposed " " args)
  (when top-level
    (emits ";\n")))

(defmethod -emit :statement
  [expr] (emit-statement expr))

(defn emit-aget [{:keys [args top-level env]}]
  (assert-gte-count args 2 'aget env)
  (-emit (first args))
  (doseq [index (rest args)]
    (emits "[")
    (-emit index)
    (emits "]"))
  (when top-level
    (emits ";\n")))

(defn emit-aset [{:keys [args top-level env]}]
  (assert-gte-count args 3 'aset env)
  (let [target (first args)
        indexes (-> args rest butlast)
        val (last args)]
    (-emit target)
    (doseq [index indexes]
      (emits "[")
      (-emit index)
      (emits "]"))
    (emits " = ")
    (-emit val)
    (when top-level
      (emits ";\n"))))

(defmethod -emit :maybe-class
  [expr]
  (let [s (str (:class expr))
        quoted? (= (first s) \')]
    (emits (cond-> s quoted? (subs 1)))))

(defmethod -emit :quote
  [{:keys [expr]}]
  (emits (:form expr)))

(defmethod -emit :const
  [{:keys [type val]}]
  (case type
    :nil (emits "null")
    :char (do (emits "'")
              (emits val)
              (emits "'"))
    (emits (pr-str val))))

(defmethod -emit :local
  [{:keys [form]}]
  (emits form))

(defmethod -emit :host-field
  [{:keys [field target]}]
  (-emit target)
  (emits ".")
  (let [is-numeric-field (try (Integer/parseInt (name field))
                              (catch Exception _))]
    (if is-numeric-field
      (do (emits "@\"")
          (emits field)
          (emits "\""))
      (emits field))))

(defmethod -emit :host-interop
  [{:keys [m-or-f target top-level]}]
  (-emit target)
  (emits ".")
  (emits m-or-f)
  (emits "()")
  (when top-level
    (emits ";\n")))

(defmethod -emit :host-call
  [{:keys [method target args top-level env] :as node}]
  (cond
    (=  method 'aget)
    (emit-aget node)

    (=  method 'aset)
    (emit-aset node)

    (=  method 'equiv)
    (emit-operator '== args node)

    ;; for now no-op and rely on zig casting, maybe modify in future
    (= method 'intCast)
    (do (assert-arg-count args 1 'intCast env)
        (-emit (first args)))

    :else (do (-emit target)
              (emits ".")
              (emits method)
              (emits "(")
              (emits-interposed ", " args)
              (emits ")")
              (when top-level
                (emits ";\n")))))

(defmethod -emit :vector
  [{:keys [items meta]}]
  (let [tag (-> meta :form :tag)]
    (emits (or tag "."))
    (emits "{ ")
    (emits-interposed ", " items)
    (emits " }")))

(defmethod -emit :map
  [{:keys [keys vals meta env]}]
  (let [tag (-> meta :form :tag)]
    (if (empty? keys)
      (do
        (when tag
          (emits tag))
        (emits "{}"))
      (do (emits (or tag "."))
          (emits "{\n")
          (doseq [[k v] (map vector keys vals)]
            (when (not= (:type k) :keyword)
              (throw (ex-info (str "Key in {} map literal expected to be a keyword, but given `" (pr-str (:form k)) "` which is a " (cond-> (:type k) keyword? name))
                              (ana.utils/source-info env))))
            (emits ".")
            (emits (name (:val k)))
            (emits " = ")
            (-emit v)
            (emits ",\n"))
          (emits "}")))))

(defmethod -emit :with-meta
  [{:keys [expr meta raw-forms top-level]}]
  (-emit (assoc expr :meta meta
                :parent-raw-forms raw-forms
                :top-level top-level)))

(defmethod -emit :invoke
  [{f :fn :keys [args top-level env] :as expr}]
  ;; TODO: perhaps move const into analysis pass
  (cond
    (#{'const 'vari} (:class f))
    (do (assert-arg-count args 2 (:class f) env)
        (let [{:keys [form]} (first args)
              {:keys [tag threadlocal comptime pub align]} (meta form)]
          (when pub
            (emits "pub "))
          (when comptime
            (emits "comptime "))
          (when threadlocal
            (emits "threadlocal "))
          (emits (if (= (:class f) 'vari) "var" (:class f)))
          (emits " ")
          (emits form)
          (when tag
            (emits ": ")
            (emits tag))
          (when align
            (emits " align(")
            (emits align)
            (emits ")")))
        (emits " = ")
        (-emit (second args))
        (when top-level
          (emits ";\n")))

    (and (= (:op f) :var)
         (= (:form f) 'clojure.core/deref))
    (do
      (assert-arg-count expr 1)
      (emits "@")
      (-emit (first args)))

    (and (= (:op f) :maybe-class)
         (= (:class f) 'slice))
    (let [[target begin end sentinel] args]
      (assert-gte-count args 2 'slice env)
      (assert-lte-count args 4 'slice env)
      (-emit target)
      (emits "[")
      (-emit begin)
      (emits "..")
      (when end
        (-emit end))
      (when sentinel
        (emits " :")
        (-emit sentinel))
      (emits "]")
      (when top-level
        (emits ";\n")))

    (= (:form f) 'struct)
    (do
      (when (-> f :form meta :extern)
        (emits "extern "))
      (when (-> f :form meta :packed)
        (emits "packed "))
      (emits "struct {\n")
      (doseq [{:keys [form op] :as arg} args]
        (if (#{:maybe-class :var} op)
          (let [tag (-> form meta :tag)]
            (assert tag)
            (emits form)
            (emits ": ")
            (emits tag)
            (when-some [default (-> form meta :default)]
              (emits " = ")
              ;; TODO this will likely be incorrect for more complicated values, fix with analyzer rewrite
              (emits default))
            (emits ",\n"))
          (-emit (assoc arg :top-level true))))
      (emits "}")
      (when top-level
        (emits ";\n")))

    (= (:form f) 'union)
    (do
      (when (-> f :form meta :extern)
        (emits "extern "))
      (when (-> f :form meta :packed)
        (emits "packed "))
      (emits "union")
      (when-some [tag (-> expr :meta :tag)]
        (emits "(")
        (emits tag)
        (emits ")"))
      (emits " {\n")
      (doseq [{:keys [form op] :as arg} args]
        (if (#{:maybe-class :var} op)
          (do (emits form)
              ;; tag can be omitted and rely on inference
              (when-some [tag (-> form meta :tag)]
                (emits ": ")
                (emits tag))
              (emits ",\n"))
          (-emit arg)))
      (emits "}")
      (when top-level
        (emits ";\n")))

    (= (:form f) 'enum)
    (do
      (when (-> f :form meta :extern)
        (emits "extern "))
      (when (-> f :form meta :packed)
        (emits "packed "))
      (emits "enum")
      (when-some [tag (-> expr :meta :tag)]
        (emits "(")
        (emits tag)
        (emits ")"))
      (emits " {\n")
      (doseq [{:keys [form op] :as arg} args]
        (if (#{:maybe-class :var} op)
          (do (emits form)
              (emits ",\n"))
          (if (= op :set!)
            (do
              (-emit arg)
              (emits ",\n"))
            (-emit (assoc arg :top-level true)))))
      (emits "}")
      (when top-level
        (emits ";\n")))

    (= (:form f) 'error)
    (do (emits "error {\n")
        (doseq [arg args]
          (-emit arg)
          (emits ",\n"))
        (emits "}")
        (when top-level
          (emits "\n")))

    (and (= (:op f) :maybe-class)
         (binary-ops (:class f)))
    (emit-operator (:class f) args expr)

    (or (= (:form f) 'while))
    (do (emits "while (")
        (-emit (first args))
        (emits ") ")
        (if (zero? (count (rest args)))
          (emits "{}")
          (maybe-emit-block (rest args) top-level)))

    (or (= (:form f) 'while-step))
    (do (emits "while (")
        (-emit (assoc (first args) :in-statement true))
        (emits ") : (")
        (-emit (assoc (second args) :in-statement true))
        (emits ")")
        (emit-block (drop 2 args)))

    (or (= (:form f) 'else))
    (do (assert (= (count args) 2))
        (-emit (first args))
        (emits " else ")
        (-emit (assoc (second args) :top-level top-level)))

    (or (and (= (:op f) :var)
             (= (:form f) 'for))
        (and (= (:op f) :maybe-class)
             (= (:class f) 'for)))
    (emit-for args)

    (= (:form f) 'test)
    (do
      (emits "test ")
      (-emit (first args))
      (emits " ")
      (emit-block (rest args)))

    (#{'comptime} (:form f))
    (do
      (emits (:form f))
      (emits " ")
      (emit-block args))

    (= (:form f) 'not=)
    (emit-operator '!= args expr)

    (#{'not 'clojure.core/not} (:form f))
    (emit-operator '! args expr)

    (= (:form f) 'bit-xor)
    (emit-operator "^" args expr)

    (= (:form f) 'bit-not)
    (emit-operator "~" args expr)

    (= (:form f) 'aget)
    (emit-aget expr)

    (= (:form f) 'aset)
    (emit-aset expr)

    (= (:form f) 'zig*)
    (emits (:val (first args)))

    (= (:form f) 'bind)
    (do (emits "|")
        (-emit (first args))
        (emits "| ")
        (if (zero? (count (rest args)))
          (emits "{}")
          (maybe-emit-block (rest args) top-level)))

    (= (:form f) 'range)
    (do (assert (= (count args) 2))
        (-emit (first args))
        (emits "...")
        (-emit (second args)))

    (= (:form f) 'case)
    (do (emits "switch (")
        (-emit (first args))
        (emits ") {\n")
        (doseq [[test then] (->> args rest (partition 2))]
          (if (= (:op (unwrap-meta test)) :vector)
            (emits-interposed ",\n" (:items (unwrap-meta test)))
            (-emit test))
          (emits " => ")
          (-emit then)
          (emits ",\n"))
        (when-let [then (and (odd? (count (rest args)))
                             (last args))]
          (emits "else => ")
          (-emit then)
          (emits ",\n"))
        (emits "}"))

    (= (:form f) 'block)
    (do (assert (= (-> (first args) :type) :keyword))
        (emits (-> (first args) :val name))
        (emits ": ")
        (maybe-emit-block (rest args) top-level))

    (= (:form f) 'suspend)
    (do (emits (:form f))
        (if (pos? (count args))
          (do (emits " ")
              (maybe-emit-block args top-level))
          (when top-level
            (emits ";\n"))))

    (= (:form f) 'inline)
    (do (assert-arg-count expr 1)
        (emits "inline ")
        (-emit (assoc (first args) :top-level top-level)))

    (= (:form f) 'comment)
    nil

    :else
    (do
      (-emit f)
      (emits "(")
      (emits-interposed ", " args)
      (emits ")")
      (when top-level
        (emits ";\n")))))

(defmethod -emit :fn
  [{:keys [local methods top-level raw-forms parent-raw-forms]}]
  (assert (= (count methods) 1))
  (let [name (:name local)
        meta-node (or name
                      (-> parent-raw-forms first first)
                      (-> raw-forms first first))
        {:keys [tag export pub extern]} (meta meta-node)]
    (when extern
      (emits "extern ")
      (emits (pr-str extern))
      (emits " "))
    (when export
      (emits "export "))
    (when pub
      (emits "pub "))
    (emits "fn ")
    (emits name)
    ;; :fn-method
    (let [{:keys [params body]} (first methods)]
      (emits "(")
      (emits-interposed ", " params
                        (fn [{:keys [op name]}]
                          (assert (= op :binding))
          ;; params is :op :binding but lets do it manually for now because :binding could have different meaning elsewhere
                          (when (-> name meta :comptime)
                            (emits "comptime "))
                          (emits name)
                          (emits ": ")
                          (emits (:tag (meta name)))))
      (emits ") ")
      (emits tag)
      ;; body is :op :do
      ;; maybe explicit return is not needed and we will get :context :ctx/retur for free
      (if (and (empty? (:statements body))
               (= (:op (:ret body)) :const)
               (= (:type (:ret body)) :nil))
        (when top-level
          (emits ";\n"))
        (do
          (emits " ")
          (assert (= (:op body) :do))
          (-emit body)
          (when top-level
            (emits "\n")))))))

(defmethod -emit :if
  [{:keys [test then else top-level]}]
  (let [has-else (not (and (= (:op else) :const)
                           (= (:type else) :nil)))]
    (emits "if (")
    ;; if the test is const then detect truthy value to support default value idiom for `cond`
    (if (and (= (:op test) :const)
             (:literal? test)
             (:val test))
      (emits "true")
      (-emit (assoc test :in-statement true)))
    (emits ") ")
    (-emit (assoc then
                  :top-level (and top-level (not has-else))))
    (when has-else
      (emits " else ")
      (-emit (assoc else :top-level top-level)))))

(defmethod -emit :do
  [{:keys [statements ret]}]
  (emits "{\n")
  (doseq [expr statements]
    (-emit (assoc expr :top-level true)))
  (when (and (not= (:op ret) :const)
             (not= (:type ret) :nil))
    (-emit (assoc ret :top-level true)))
  ;; perhaps auto-label blocks to make auto return work
  (emits "}\n"))

(defmethod -emit :set!
  [{:keys [target val top-level]}]
  (-emit target)
  (emits " = ")
  (-emit (assoc val :in-statement true))
  (when top-level
    (emits ";\n")))

(defmethod -emit :try
  [{:keys [body catches top-level env] :as node}]
  (case (count catches)
    0 (do (assert (= (:op body) :do))
          (assert (empty? (:statements body)))
          (emits "try ")
          (-emit (:ret body))
          (when top-level
            (emits ";\n")))
    1 (do (assert (= (:op body) :do))
          (assert (empty? (:statements body)))
          (-emit (:ret body))
          (emits " catch |")
          (emits (-> (first catches) :local :name))
          (emits "| ")
          (-emit (-> (first catches) :body))
          (when top-level
            (emits ";\n")))
    (throw (ex-info (str "try was given " (count catches) " catch clauses, but only 1 is supported")
                    (ana.utils/source-info env)))))

(defmethod -emit :default
  [expr]
  (throw (ex-info (str "Unhandled op for -emit: " (:op expr) " children: " (:children expr))
                  {:node expr})))
