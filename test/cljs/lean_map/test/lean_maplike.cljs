(ns cljs.lean-map.test.lean-maplike
  (:require
    [cljs.lean-map.test.shared :as shared]
    [cljs.test :refer-macros [deftest]]
    [collection-check.core :as cc]))

(deftest assert-lean-map-core-map-like-for-lean-map
  (cc/assert-map-like 100 shared/empty shared/gen-key shared/gen-value {:base shared/empty}))
