(ns cljs.lean-map.util
  (:require [cljs.lean-map.core :as c]
            [goog.object :as gobj])
  (:refer-clojure :exclude [empty hash-map]))

(def use-lean-map (atom false))

(def cljs-persistent-assoc (.-cljs$core$IAssociative$_assoc$arity$3 (gobj/clone (.-prototype PersistentArrayMap))))
(def cljs-transient-assoc (.-cljs$core$ITransientAssociative$_assoc_BANG_$arity$3 (gobj/clone (.-prototype TransientArrayMap))))

(defn- array->transient-lean-map [len arr]
  (loop [out (transient (.-EMPTY c/PersistentHashMap))
         i   0]
    (if (< i len)
      (recur (assoc! out (aget arr i) (aget arr (inc i))) (+ i 2))
      out)))

(defn- lean-transient-assoc [tcoll key val]
  (if (.-editable? tcoll)
    (let [idx (array-map-index-of tcoll key)]
      (if (== idx -1)
        (if (<= (+ (.-len tcoll) 2) (* 2 (.-HASHMAP-THRESHOLD PersistentArrayMap)))
          (do (set! (.-len tcoll ) (+ (.-len tcoll) 2))
              (.push (.-arr tcoll) key)
              (.push (.-arr tcoll) val)
              tcoll)
          (assoc! (array->transient-lean-map (.-len tcoll) (.-arr tcoll)) key val))
        (if (identical? val (aget (.-arr tcoll) (inc idx)))
          tcoll
          (do (aset (.-arr tcoll) (inc idx) val)
              tcoll))))
    (throw (js/Error. "assoc! after persistent!"))))

(defn- lean-persistent-assoc [coll k v]
  (let [idx (array-map-index-of coll k)]
    (cond
      (== idx -1)
      (if (< (.-cnt coll) (.-HASHMAP-THRESHOLD PersistentArrayMap))
        (let [arr (array-map-extend-kv coll k v)]
          (PersistentArrayMap. meta (inc (.-cnt coll)) arr nil))
        (-> (into (.-EMPTY c/PersistentHashMap) coll)
            (-assoc k v)
            (-with-meta meta)))

      (identical? v (aget (.-arr coll) (inc idx)))
      coll

      :else
      (let [arr (doto (aclone (.-arr coll))
                  (aset (inc idx) v))]
        (PersistentArrayMap. meta (.-cnt coll) arr nil)))))

(def empty (.-EMPTY c/PersistentHashMap))

(defn hash-map
  "keyval => key val
  Returns a new lean map with supplied mappings."
  [& keyvals]
  (loop [in (seq keyvals), out (transient (.-EMPTY c/PersistentHashMap))]
    (if in
      (recur (nnext in) (assoc! out (first in) (second in)))
      (persistent! out))))

(defn set-maps-to-lean-map! []
  "Makes Persistent and Transient ArrayMaps convert to Lean Maps at the HashMap threshold
  (current 8 key value pairs)"
  (set! (.-cljs$core$IAssociative$_assoc$arity$3 (.-prototype PersistentArrayMap)) lean-persistent-assoc)
  (set! (.-cljs$core$ITransientAssociative$_assoc_BANG_$arity$3 (.-prototype TransientArrayMap)) lean-transient-assoc)
  (reset! use-lean-map true))

(defn set-maps-to-cljs-map! []
  "Makes Persistent and Transient ArrayMaps convert to CLJS Maps at the HashMap threshold
  (current 8 key value pairs)"
  (set! (.-cljs$core$IAssociative$_assoc$arity$3 (.-prototype PersistentArrayMap)) cljs-persistent-assoc)
  (set! (.-cljs$core$ITransientAssociative$_assoc_BANG_$arity$3 (.-prototype TransientArrayMap)) cljs-transient-assoc)
  (reset! use-lean-map false))

(defn using-lean-maps? []
  "Is ClojureScript currently using Lean Maps or the default CLJS Map implementation"
  @use-lean-map)

(defn lean-map? [m]
  "Check if a map is an instance of lean-map"
  (or (instance? c/PersistentHashMap m) (instance? c/TransientHashMap m)))

(defn lean-map-seq? [s]
  "Checks if a map is an instance of the lean-map seq"
  (instance? c/NodeSeq s))