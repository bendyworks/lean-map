(ns cljs.lean-map.test.core
  (:require
    [cljs.test :refer-macros [deftest]]
    [collection-check.core :as cc]
    [clojure.test.check.generators :as gen]
    [cljs.lean-map.core :as lean-map.core]))

(defrecord BadHashNumber [num])

(extend-protocol IHash
  BadHashNumber
  (-hash [_]
    1))

(def gen-bad-hash
  (gen/fmap (partial apply ->BadHashNumber) (gen/tuple gen/int)))

(def gen-key
  (gen/tuple (gen/frequency [[9 gen/int] [1 gen-bad-hash]])))

(def gen-value
  (gen/tuple gen/int))

(deftest assert-lean-map-core-map-like
  (cc/assert-map-like 100 (.-EMPTY lean-map.core/PersistentHashMap) gen-key gen-value))
