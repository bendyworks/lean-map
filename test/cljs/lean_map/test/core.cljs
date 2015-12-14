(ns cljs.lean-map.test.core
  (:require
    [cljs.lean-map.test.shared :as shared]
    [cljs.test :refer-macros [deftest is]]
    [clojure.test.check.generators :as gen]))

(def hash-collision-map
  (assoc shared/mempty 0 0 1 1 (shared/BadHashNumber. 2) 2 3 3 (shared/BadHashNumber. 4) 4))

(def hash-collision-map-same
  (assoc shared/mempty (shared/BadHashNumber. 2) 2 (shared/BadHashNumber. 4) 4 3 3 0 0 1 1))

(def hash-collision-map-different-key
  (assoc shared/mempty 0 0 1 1 (shared/BadHashNumber. 8) 2 3 3 (shared/BadHashNumber. 4) 4))

(def hash-collision-map-different-value
  (assoc shared/mempty 0 0 1 1 (shared/BadHashNumber. 2) 8 3 3 (shared/BadHashNumber. 4) 4))

(deftest equal-hash-collision-maps
  (is (= hash-collision-map hash-collision-map-same))
  (is (not= hash-collision-map hash-collision-map-different-key))
  (is (not= hash-collision-map hash-collision-map-different-value)))

(deftest equal-hash-collision-map-hashes
  (is (== (hash hash-collision-map) (hash hash-collision-map-same)))
  (is (== (hash hash-collision-map) (hash hash-collision-map-different-key)))
  (is (not= (hash hash-collision-map) (hash hash-collision-map-different-value))))