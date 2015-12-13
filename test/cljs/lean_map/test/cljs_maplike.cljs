(ns cljs.lean-map.test.cljs-maplike
  (:require
    [cljs.lean-map.test.shared :as shared]
    [cljs.test :refer-macros [deftest]]
    [collection-check.core :as cc]))

(deftest assert-lean-map-core-map-like-for-cljs-map
  (cc/assert-map-like 100 shared/empty shared/gen-key shared/gen-value))
