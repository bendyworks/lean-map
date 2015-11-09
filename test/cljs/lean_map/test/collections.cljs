(ns cljs.lean-map.test.collections
  (:require
    [clojure.test.check.generators :as gen]
    [cljs.lean-map.test.utils :as utils]
    [com.gfredericks.test.chuck.clojure-test :as chuck :include-macros true]
    [cljs.test :refer [do-report]])
  (:require-macros
    [cljs.test :refer [is]]
    [clojure.test.check :refer [quick-check]]
    [cljs.lean-map.test.macros :as macros]))

(defn gen-meta [gen]
  (gen/fmap
    (fn [[x meta]]
      (if (instance? cljs.core/IWithMeta x)
        (with-meta x {:foo meta})
        x))
    (gen/tuple
      gen
      (gen/one-of [gen/int (gen/return nil)]))))

(defn- tuple* [& args]
  (->> args
       (map
         #(if (and (map? %) (contains? % :gen))
           %
           (gen/return %)))
       (apply gen/tuple)))

(defn- ttuple [& args]
  (gen/tuple (apply tuple* args)))

(defn- seq-actions [element-generator]
  (gen/fmap
    (fn [[pre actions post]]
      (concat [pre] actions [post]))
    (tuple*
      [:seq]
      (gen/list
        (gen/one-of
          [(tuple* :rest)
           (tuple* :map-id)
           (tuple* :cons element-generator)
           (tuple* :conj element-generator)]))
      [:into])))

(defn- transient-actions [& intermediate-actions]
  (gen/fmap
    (fn [[pre actions post]]
      (concat [pre] actions [post]))
    (tuple*
      [:transient]
      (gen/list
        (gen/one-of
          (vec intermediate-actions)))
      [:persistent!])))

(defn- gen-map-actions [key-generator value-generator transient? ordered?]
  (let [key-generator (gen-meta key-generator)
        value-generator (gen-meta value-generator)
        standard [(ttuple :dissoc key-generator)
                  (ttuple :assoc key-generator value-generator)]]
    (gen/list
      (gen/one-of
        (concat
          standard
          (when transient?
            [(transient-actions
               (tuple* :dissoc! key-generator)
               (tuple* :assoc! key-generator value-generator))])
          (when ordered?
            [(seq-actions (tuple* key-generator value-generator))]))))))

(defn- build-collections
  "Given a list of actions, constructs two parallel collections that can be compared
   for equivalencies."
  [coll-a coll-b vector-like? actions]
  (let [orig-a coll-a
        orig-b coll-b]
    (reduce
      (fn [[coll-a coll-b prev-actions] [action x y :as act]]

        ;; if it's a vector, we need to choose a valid index
        (let [idx (when (and vector-like? (#{:assoc :assoc!} action))
                    (if (< 900 x)
                      (count coll-a)
                      (int (* (count coll-a) (/ x 1e3)))))
              [action x y :as act] (if idx
                                     [action idx y]
                                     act)
              f (case action
                  :cons #(cons x %)
                  :rest rest
                  :map-id #(map identity %)
                  :seq seq
                  :into [#(into (empty orig-a) %) #(into (empty orig-b) %)]
                  :persistent! persistent!
                  :transient transient
                  :pop #(if (empty? %) % (pop %))
                  :pop! #(if (= 0 (count %)) % (pop! %))
                  :conj #(conj % x)
                  :conj! #(conj! % x)
                  :disj #(disj % x)
                  :disj! #(disj! % x)
                  :assoc #(assoc % x y)
                  :assoc! #(assoc! % x y)
                  :dissoc #(dissoc % x)
                  :dissoc! #(dissoc! % x))
              f-a (if (vector? f) (first f) f)
              f-b (if (vector? f) (second f) f)]

          [(f-a coll-a)
           (f-b coll-b)
           (conj prev-actions act)]))
      [coll-a coll-b []]
      (apply concat actions))))

(defn assert-equivalent-collections
  [a b]
  (is (= (count a) (count b)))
  (is (= a b))
  (is (= b a))
  (is (-equiv a b))
  (is (-equiv b a))
  (is (= (hash a) (hash b)))
  (is (= (hash a) (hash b)))
  (is (= a b
         (into (empty a) a)
         (into (empty b) b)
         (into (empty a) b)
         (into (empty b) a)))
  (is (= (into (empty a) (take 1 a))
         (reduce #(reduced (conj %1 %2)) (empty a) a)))
  (is (= (into (empty b) (take 1 b))
         (reduce #(reduced (conj %1 %2)) (empty b) b))))

(defn- meta-map [s]
  (->> s (map #(vector % (meta %))) (into {})))

(defn assert-equivalent-maps [a b]
  (assert-equivalent-collections a b)
  (is (= (set (keys a)) (set (keys b))))
  (let [ks (keys a)]
    (is (= (map #(get a %) ks)
           (map #(get b %) ks)))
    (is (= (map #(a %) ks)
           (map #(b %) ks)))
    (is (= (map #(-lookup a %) ks)
           (map #(-lookup b %) ks))))
  (is (and
        (every? #(= (key %) (first %)) a)
        (every? #(= (key %) (first %)) b)))
  (is (= (meta-map (keys a)) (meta-map (keys b))))
  (is (every? #(= (meta (a %)) (meta (b %))) (keys a)))
  (is (every? #(= (val %) (a (key %)) (b (key %))) a)))

(defn- transient? [x]
  (implements? cljs.core/IEditableCollection x))

(defn assert-map-like
  ([empty-coll key-generator value-generator]
   (assert-map-like 1e3 empty-coll key-generator value-generator nil))
  ([n empty-coll key-generator value-generator]
   (assert-map-like n empty-coll key-generator value-generator nil))
  ([n empty-coll key-generator value-generator
    {:keys [base ordered?]
     :or {ordered? false
          base {}}}]
   (macros/reporting-failing-actions
     (chuck/checking "map-like" n
                     [actions (gen-map-actions key-generator value-generator (transient? empty-coll) ordered?)]
                     (let [[a b actions] (build-collections empty-coll base false actions)]
                       (assert-equivalent-maps a b))))))
