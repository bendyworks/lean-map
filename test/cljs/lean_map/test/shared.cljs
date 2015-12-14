(ns cljs.lean-map.test.shared
  (:refer-clojure :exclude [empty])
  (:require
    [cljs.lean-map.core :as lm]
    [clojure.test.check.generators :as gen]))

(def mempty (.-EMPTY lm/PersistentHashMap))

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
