(ns cljs.lean-map.test.util
  (:require
    [cljs.test :refer-macros [deftest is]]
    [cljs.lean-map.core :as core]
    [cljs.lean-map.util :as util]))

(defn persistent-map []
  (assoc {} 0 0 1 1 2 2 3 3 4 4 5 5 6 6 7 7 8 8 9 9))

(defn transient-map []
  (assoc! (transient {}) 0 0 1 1 2 2 3 3 4 4 5 5 6 6 7 7 8 8 9 9))

(deftest array-map-expands-properly-on-conversion-to-lean
  (util/set-maps-to-lean-map!)
  (is (= (instance? core/PersistentHashMap (persistent-map)) true))
  (is (= (instance? core/TransientHashMap (transient-map)) true))
  (util/set-maps-to-cljs-map!))

(deftest array-map-converts-back-to-cljs-correctly
  (util/set-maps-to-cljs-map!)
  (is (= (instance? PersistentHashMap (persistent-map)) true))
  (is (= (instance? TransientHashMap (transient-map)) true))
  (util/set-maps-to-lean-map!)
  (util/set-maps-to-cljs-map!)
  (is (= (instance? PersistentHashMap (persistent-map)) true))
  (is (= (instance? TransientHashMap (transient-map)) true))
  (util/set-maps-to-cljs-map!))

(deftest usage-function-correctly-shows-lean-or-cljs-map
  (util/set-maps-to-cljs-map!)
  (is (= (util/using-lean-maps?) false))
  (util/set-maps-to-lean-map!)
  (is (= (util/using-lean-maps?) true))
  (util/set-maps-to-cljs-map!))

(deftest type-checks-work
  (is (= (util/lean-map? util/empty) true))
  (is (= (util/lean-map? (transient util/empty)) true))
  (is (= (util/lean-map-seq? (seq (assoc util/empty 1 1))) true)))

(deftest lean-map-hash-map
  (is (= (util/hash-map :a :a 1 1) (assoc util/empty :a :a 1 1))))