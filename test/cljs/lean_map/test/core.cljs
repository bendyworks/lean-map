(ns cljs.lean-map.test.core
  (:require
    [clojure.test.check.generators :as gen]
    [cljs.lean-map.test.collections :as colls])
  (:require-macros
    [cljs.test :as ctest]))

(def gen-element
  (gen/tuple gen/int))

(ctest/deftest test-identities
  (colls/assert-map-like 100 {} gen-element gen-element))

(comment
  (require '[cljs.pprint :as pp])
  (ctest/run-tests))
