(ns clojure.lean-map.test
  (:require [clojure.lean-map.util :as lmu]
            [clojure.test :as t]
            [clojure.test.check.generators :as gen]
            [collection-check :as cc]))

(defn seq-iter-match
  [^clojure.lang.Seqable seqable ^Iterable iterable]
  (if (nil? iterable)
    (when (not (nil? (seq seqable)))
      (throw (ex-info "Null iterable but seq has elements"
                      {:pos 0 :seqable seqable :iterable iterable})))
    (let [i (.iterator iterable)]
      (loop [s (seq seqable)
             n 0]
        (if (seq s)
          (do
            (when-not (.hasNext i)
              (throw (ex-info "Iterator exhausted before seq"
                              {:pos n :seqable seqable :iterable iterable})))
            (when-not (= (.next i) (first s))
              (throw (ex-info "Iterator and seq did not match"
                              {:pos n :seqable seqable :iterable iterable})))
            (recur (rest s) (inc n)))
          (when (.hasNext i)
            (throw (ex-info "Seq exhausted before iterator"
                            {:pos n :seqable seqable :iterable iterable}))))))))


(t/deftest meta-operations
  (t/is (= {:foo :bar} (-> lmu/empty (with-meta {:foo :bar}) meta)))
  (t/is (= {:foo :bar} (-> lmu/empty (with-meta {:foo :bar}) (assoc :foo :bar) meta))))

(t/deftest assoc-operations
  (t/is (= :bar (-> lmu/empty (assoc :foo :bar) (get :foo))))
  (t/is (= :baz (-> lmu/empty (assoc :whee :bar) (get :foo :baz)))))

(t/deftest assoc-operations-with-hash-collisions
  (t/is (= :bar1 (-> lmu/empty (assoc :key70327 :bar1 :key101439 :bar2) (get :key70327))))
  (t/is (= :bar2 (-> lmu/empty (assoc :key70327 :bar1 :key101439 :bar2) (get :key101439)))))

(t/deftest transient-assoc-operations
  (t/is (= :bar (-> lmu/empty transient (assoc! :foo :bar) persistent! (get :foo))))
  (t/is (= :baz (-> lmu/empty transient (assoc! :whee :bar) persistent! (get :foo :baz)))))

(t/deftest transient-assoc-operations-with-hash-collisions
  (t/is (= :bar1 (-> lmu/empty transient (assoc! :key70327 :bar1 :key101439 :bar2) persistent! (get :key70327))))
  (t/is (= :bar2 (-> lmu/empty transient (assoc! :key70327 :bar1 :key101439 :bar2) persistent! (get :key101439)))))

(t/deftest dissoc-operations
  (t/is (= [:foo1] (-> lmu/empty (assoc :foo :bar :foo1 :bar1) (dissoc :foo) keys vec))))

(t/deftest dissoc-operations-with-hash-collisions
  (t/is (= [:key101439] (-> lmu/empty (assoc :key70327 :bar1 :key101439 :bar2) (dissoc :key70327) keys vec)))
  (t/is (= :bar2 (-> lmu/empty (assoc :key70327 :bar1 :key101439 :bar2) (dissoc :key70327) (get :key101439 :bar0)))))

(t/deftest transient-dissoc-operations
  (t/is (= [:foo1] (-> lmu/empty (assoc :foo :bar :foo1 :bar1) transient (dissoc! :foo) persistent! keys vec))))

(t/deftest transient-dissoc-operations-with-hash-collisions
  (t/is (= [:key101439] (-> lmu/empty (assoc :key70327 :bar1 :key101439 :bar2) transient (dissoc! :key70327) persistent! keys vec)))
  (t/is (= :bar2 (-> lmu/empty (assoc :key70327 :bar1 :key101439 :bar2) transient (dissoc! :key70327) persistent! (get :key101439 :bar0)))))

(t/deftest hash-map-operations
  (t/is (= (lmu/hash-map :foo :bar) (-> lmu/empty (assoc :foo :bar))))
  (t/is (= :bar (-> (lmu/hash-map :foo :bar) (get :foo)))))

(t/deftest seq-operations
  (t/is (= false (-> lmu/empty (assoc :foo :bar) seq empty?)))
  (t/is (= [:foo :bar] (-> lmu/empty (assoc :foo :bar) seq first))))

(t/deftest kv-reduce-operations
  (t/is (= 45 (let [kvm (->> (zipmap (range 10) (range 10)) (into lmu/empty))]
                (.kvreduce kvm (fn [sum _ v] (+ sum v)) 0)))))

(t/deftest reduce-operations
  (t/is (= 45 (let [kvm (->> (zipmap (range 10) (range 10)) (into lmu/empty))]
                (reduce (fn [sum [_ v]] (+ sum v)) 0 kvm)))))

(t/deftest seq-and-iter-match
  (let [kvm (->> (zipmap (range 100) (range 100)) (into lmu/empty))]
    (t/is (= nil (try (seq-iter-match kvm kvm) (catch Exception e e))))))

(deftype BadHashNumber [num]
  Object
  (toString [_]
    (str "BadHashNumber. " num))
  clojure.lang.IHashEq
  (hasheq [_]
    1))

(def gen-bad-hash
  (gen/fmap (partial apply ->BadHashNumber) (gen/tuple gen/int)))

(def gen-key
  (gen/tuple (gen/frequency [[9 gen/int] [1 gen-bad-hash]])))

(def gen-value
  (gen/tuple gen/int))

(t/deftest assert-lean-map-core-map-like-for-lean-map
  (cc/assert-map-like 1e3 lmu/empty gen-key gen-value {:base lmu/empty}))

(t/deftest assert-lean-map-core-map-like-for-clj-map
  (cc/assert-map-like 1e3 lmu/empty gen-key gen-value {:base (hash-map)}))