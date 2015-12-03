(ns cljs.lean-map.test.core
  (:require
    [cljs.test :refer-macros [deftest]]
    [collection-check.core :as cc]
    [clojure.test.check.generators :as gen]
    [cljs.lean-map.core :as lean-map.core]))

(def gen-element
  (gen/tuple gen/int))

(deftest assert-lean-map-core-map-like
  (cc/assert-map-like 100 (.-EMPTY lean-map.core/PersistentHashMap) gen-element gen-element))
