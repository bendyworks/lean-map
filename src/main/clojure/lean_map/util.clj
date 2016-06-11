(ns clojure.lean-map.util
  (:import [clojure.lang LeanMap])
  (:refer-clojure :exclude [empty hash-map]))

(def empty LeanMap/EMPTY)

(defn hash-map
  "keyval => key val
  Returns a new lean map with supplied mappings."
  [& keyvals]
  (throw (UnsupportedOperationException. "hash-map operation is not supported yet")))