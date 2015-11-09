(ns cljs.lean-map.test.utils)

(defn pr-meta [v]
  (if-let [m (meta v)]
    `(with-meta ~v ~m)
    v))

(defn describe-action [[f & rst]]
  (case f
    :cons (list '->> (list* 'cons (map pr-meta rst)))
    :into '(into (empty coll))
    (if (empty? rst)
      (symbol (name f))
      (list*
        (symbol (name f))
        (map pr-meta rst)))))

(defn report-failing-actions [x]
  (when (and (= :fail (:type x))
             (get-in x [:message :shrunk]))
    (let [actions (get-in x [:message :shrunk :smallest])]
      (println "\n  actions = " (->> actions
                                     first
                                     (apply concat)
                                     (map describe-action)
                                     (list* '-> 'coll)
                                     pr-str)
               "\n"))))
