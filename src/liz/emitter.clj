(ns liz.emitter
  (:require [clojure.pprint :refer [pprint]]
            [liz.lang :refer [binary-ops]]))

(defn unwrap-meta [expr]
  (cond-> expr
          (= (:op expr) :with-meta) :expr))

(defmulti -emit :op)

(defn emit [ast]
  (-emit ast))

(defn emits
  ([a] (.write *out* (str a)))
  ([a & xs] (emits a) (doseq [x xs] (emits x))))

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

(defn emit-aget [{:keys [args top-level]}]
  (-emit (first args))
  (doseq [index (rest args)]
    (emits "[")
    (-emit index)
    (emits "]"))
  (when top-level
    (emits ";\n")))

(defmethod -emit :maybe-class
  [expr]
  (let [s (str (:class expr))
        quoted? (= (first s) \')]
    (emits (cond-> s quoted? (subs 1)))))

(defmethod -emit :var
  [{:keys [form]}]
  (emits form))

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
  [{:keys [method target args top-level] :as node}]
  (cond
    (=  method 'aget)
    (emit-aget node)

    (=  method 'aset)
    (do (assert (= (count args) 3))
        (let [[target index val] args]
           (-emit target)
           (emits "[")
           (-emit index)
           (emits "] = ")
           (-emit val)
           (when top-level
             (emits ";\n"))))

    (=  method 'equiv)
    (emit-operator '== args node)

    ;; for now no-op and rely on zig casting, maybe modify in future
    (= method 'intCast)
    (do (when (not= (count args) 1)
          (throw (ex-info (str "intCast has " (count args) " args in :op :host-call, expeced 1")
                          {:node node})))
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
  [{:keys [keys vals meta]}]
  (let [tag (-> meta :form :tag)]
    (if (empty? keys)
      (do
        (when tag
          (emits tag))
        (emits "{}"))
      (do (emits (or tag "."))
          (emits "{\n")
          (doseq [[k v] (map vector keys vals)]
            (assert (= (:type k) :keyword))
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
  [{f :fn :keys [args top-level] :as expr}]
  ;; TODO: perhaps move const into analysis pass
  (cond
    (#{'const 'vari} (:class f))
    (do (assert (= (count args) 2))
        (let [{:keys [op form]} (first args)
              _ (assert (#{:var :maybe-class} op))
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
      (assert (= (count args) 1))
      (emits "@")
      (-emit (first args)))

    (and (= (:op f) :maybe-class)
         (= (:class f) 'slice))
    (let [[target begin end] args]
       (assert (and (< 1 (count args) 4)))
       (-emit target)
       (emits "[")
       (-emit begin)
       (emits "..")
       (when end
         (-emit end))
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

    (and (= (:op f) :var)
         (binary-ops (:form f)))
    (emit-operator (:form f) args expr)

    (or (= (:form f) 'while))
    (do (emits "while (")
        (-emit (first args))
        (emits ") ")
        (if (zero? (count (rest args)))
          (emits "{}")
          (maybe-emit-block (rest args) top-level)))

    (or (= (:form f) 'while-continue))
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
      (cond
        (and (= (count args) 1)
             (= (-> args first :op) :invoke)
             (= (-> args first :fn :form) 'label))
        (-emit (first args))

        (and (= (count args) 1)
             (= (-> args first :op) :invoke)
             (= (-> args first :fn :form) 'assert))
        (emit-block args)

        :else
        (maybe-emit-block args top-level)))

    (= (:form f) 'not=)
    (emit-operator '!= args expr)

    (= (:form f) 'not)
    (emit-operator '! args expr)

    (= (:form f) 'aget)
    (emit-aget expr)

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

    (= (:form f) 'label)
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
    (do (assert (= (count args) 1))
        (emits "inline ")
        (-emit (assoc (first args) :top-level top-level)))

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
  [{:keys [body catches top-level] :as node}]
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
    (throw (ex-info (str "Try/Catch allows max 1 catch clause. but " (count catches) " given")
                    {:node node}))))

(defmethod -emit :default
  [expr]
  (throw (ex-info ("Unhandled op for -emit: " (:op expr) "children:" (:children expr))
           {:node expr})))
