(ns cljs.lean-map.profile
  (:require [cljs.lean-map.core :as lean]))

(def test-size 100000)
(def lem (.-EMPTY lean/PersistentHashMap))
(def cem (.-EMPTY cljs.core/PersistentHashMap))

(def test-keys (mapv (fn [i]
                       (let [nk (keyword (str "key" i))]
                         (hash nk)
                         nk))
                     (range test-size)))

(defn lean-assoc []
  (loop [m lem i 0]
    (if (< i test-size)
      (recur (assoc m (nth test-keys i) i) (inc i))
      m)))

(def seq-lem (lean-assoc))

(defn lean-seq []
  (loop [s (seq seq-lem)]
    (if (seq s)
      (recur (next s)))))

(defn cljs-assoc []
  (loop [m cem i 0]
    (if (< i test-size)
      (recur (assoc m (nth test-keys i) i) (inc i))
      m)))

(def seq-cem (cljs-assoc))

(defn cljs-seq []
  (loop [s (seq seq-cem)]
    (if (seq s)
      (recur (next s)))))

(defn profile [f name]
  (dotimes [i 5]
    (js/console.profile name)
    (f)
    (js/console.profileEnd)))

(defn run-profile []
  (profile cljs-assoc "ClJS Assoc")
  (profile cljs-seq "ClJS Seq")
  (profile lean-assoc "Lean Assoc")
  (profile lean-seq "Lean Seq"))
