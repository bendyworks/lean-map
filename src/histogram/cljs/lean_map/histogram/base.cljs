(ns cljs.lean-map.histogram.base
  (:require [cljs.lean-map.util :as lm]))

(defn create-op-array [h-size h-times]
  (make-array (* h-size h-times)))

(defn create-test-keys [num-of-keys]
  (mapv (fn [i]
          (let [nk (keyword (str "key" i))]
            (hash nk)
            nk))
        (range num-of-keys)))

(defn create-filled-map [em test-keys size]
  (loop [m em i 0]
    (when (< i size)
      (recur (assoc m (nth test-keys i) i) (inc i)))))

(defn assoc-test [em test-keys size times op-array total-array]
  ;;Warm up runs
  (loop [j 0]
    (loop [m em i 0]
      (when (< i size)
        (recur (assoc m (nth test-keys i) i) (inc i))))
    (when (< j (quot times 10))
      (recur (inc j))))
  ;;Histogram runs
  (loop [j 0 start-time (system-time)]
    (loop [m em i 0]
      (when (< i size)
        (let [op-start-time (system-time)
              nm (assoc m (nth test-keys i) i)]
          (aset op-array (+ (* j size) i) (- (system-time) op-start-time))
          (recur nm (inc i)))))
    (when (< j times)
      (aset total-array j (- (system-time) start-time))
      (recur (inc j) (system-time)))))

(defn num-sort [a b] (- a b))
(def histogram-percents [5 10 15 20 25 30 35 40 45 50 55 60 65 70 75 80 85 90 95 99 99.9 99.99])
(defn histogram-numbers [histogram-array histogram-percents]
  (.sort histogram-array num-sort)
  (let [hist-len (alength histogram-array)
        histo-slots  (map #(Math/floor (* hist-len (/ % 100))) histogram-percents)
        min-time (aget histogram-array 0)
        max-time (aget histogram-array (dec hist-len))]
    (-> [min-time]
        (into (map #(aget histogram-array %) histo-slots))
        (conj max-time))))

(defn get-histogram-data [size times]
  (let [test-keys (create-test-keys size)
        op-array (create-op-array size times)
        total-array (make-array times)
        current-empty (hash-map)
        current-filled (create-filled-map current-empty test-keys size)
        lean-empty (lm/hash-map)
        lean-filled (create-filled-map lean-empty test-keys size)]
    (reduce (fn [histogram [op base-map]]
              (assoc-test base-map test-keys size times op-array total-array)
              (-> histogram
                  (assoc-in [:op op] (histogram-numbers op-array histogram-percents))
                  (assoc-in [:total op] (histogram-numbers total-array histogram-percents))))
            {:assoc-size size
             :repeat-times times}
            {:current-assoc-insert current-empty
             :lean-assoc-insert lean-empty
             :current-assoc-update current-filled
             :lean-assoc-update lean-filled})))
