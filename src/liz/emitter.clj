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

(defn emit-operator [operator args {:keys [top-level]}]
  (when-not top-level
    (emits "("))
  (if (= (count args) 1)
    ;; Unary operator - perhaps will need listing in the future
    (do
      (emits operator)
      (-emit (first args)))
    (let [sep (str " " operator " ")]
      (emits-interposed sep args)))
  (if top-level
    (emits ";\n")
    (emits ")")))

(defn emit-block [forms]
  (emits "{\n")
  (doseq [form forms]
    (-emit (assoc form :top-level true)))
  (emits "}"))

(defn emit-while [args]
  (emits "while (")
  (-emit (first args))
  (emits ") ")
  (emit-block (rest args)))

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

(defmethod -emit :maybe-class
  [expr]
  (emits (:class expr)))

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
  (emits field))

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
    (do (assert (= (count args) 2))
        (let [[target index] args]
           (-emit target)
           (emits "[")
           (-emit index)
           (emits "]")
           (when top-level
             (emits ";\n"))))

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
  [{:keys [items]}]
  (emits ".{ ")
  (emits-interposed ", " items)
  (emits " }"))

(defmethod -emit :map
  [{:keys [keys vals]}]
  (if (empty? keys)
    (emits "{}")
    (do (emits ".{\n")
        (doseq [[k v] (map vector keys vals)]
          (assert (= (:type k) :keyword))
          (emits ".")
          (emits (name (:val k)))
          (emits " = ")
          (-emit v)
          (emits ",\n"))
        (emits "}"))))

(defmethod -emit :with-meta
  [{:keys [expr]}]
  (-emit expr))

(defmethod -emit :invoke
  [{f :fn :keys [args top-level] :as expr}]
  ;; TODO: perhaps move const into analysis pass
  (cond
    (#{'const 'vari} (:class f))
    (do (assert (= (count args) 2))
        (let [{:keys [op form]} (first args)
              _ (assert (#{:var :maybe-class} op))
              {:keys [tag threadlocal comptime]} (meta form)]
          (when comptime
            (emits "comptime "))
          (when threadlocal
            (emits "threadlocal "))
          (emits (if (= (:class f) 'vari) "var" (:class f)))
          (emits " ")
          (emits form)
          (when tag
            (emits ": ")
            (emits tag)))
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

    (and (= (:op f) :var)
         (= (:form f) 'struct))
    (do
      (assert (even? (count args)))
      (emits "struct {\n")
      (doseq [[k v] (partition 2 args)]
        (if (= (:type k) :keyword)
          (do (emits (name (:val k)))
              (emits ": ")
              (-emit v)
              (emits ",\n"))
          (let [{:keys [var tag]} (meta (:form k))]
            (when var (emits "var "))
            (emits (:form k))
            (emits ": ")
            (if tag
              (do (emits tag)
                  (emits " = ")
                  (-emit v))
              (-emit v))
            (emits ";\n"))))
      (emits "}")
      (when top-level
        (emits ";\n")))

    (and (= (:op f) :maybe-class)
         (= (:class f) 'array))
    (do
      (when-some [tag (-> expr :meta :tag)]
        (emits tag))
      (emits "{ ")
      (emits-interposed ", " args)
      (emits " }"))

    (and (= (:op f) :maybe-class)
         (binary-ops (:class f)))
    (emit-operator (:class f) args expr)

    (and (= (:op f) :var)
         (binary-ops (:form f)))
    (emit-operator (:form f) args expr)

    (or (and (= (:op f) :var)
             (= (:form f) 'while))
        (and (= (:op f) :maybe-class)
             (= (:class f) 'while)))
    (emit-while args)

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

    (= (:form f) 'not=)
    (emit-operator '!= args expr)

    (= (:form f) 'not)
    (emit-operator '! args expr)

    (= (:form f) 'zig*)
    (emits (:val (first args)))

    (= (:form f) 'bind)
    (do (emits "|")
        (-emit (first args))
        (emits "| ")
        (emit-block (rest args)))

    (= (:form f) 'case)
    (do (emits "switch (")
        (-emit (first args))
        (emits ") {\n")
        (doseq [[test then] (->> args rest butlast (partition 2))]
          (-emit test)
          (emits " => ")
          (-emit then)
          (emits ",\n"))
        (when-let [then (last args)]
          (emits "else => ")
          (-emit then)
          (emits ",\n"))
        (emits "}"))

    (#{'async 'suspend 'resume 'return 'defer 'errdefer 'continue 'break} (:form f))
    (emit-statement expr)

    :else
    (do
      (-emit f)
      (emits "(")
      (emits-interposed ", " args)
      (emits ")")
      (when top-level
        (emits ";\n")))))

(defmethod -emit :fn
  [{:keys [local methods top-level]}]
  (assert (= (count methods) 1))
  (let [name (:name local)
        {:keys [tag export pub]} (meta name)]
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
          (emits name)
          (emits ": ")
          (emits (:tag (meta name)))))
      (emits ") ")
      (emits tag)
      ;; body is :op :do
      ;; maybe explicit return is not needed and we will get :context :ctx/retur for free
      (emits " ")
      (assert (= (:op body) :do))
      (-emit body))))

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
      (-emit test))
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
  (-emit (assoc ret :top-level true))
  ;; perhaps auto-label blocks to make auto return work
  (emits "}\n"))

(defmethod -emit :set!
  [{:keys [target val top-level]}]
  (-emit target)
  (emits " = ")
  (-emit val)
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
