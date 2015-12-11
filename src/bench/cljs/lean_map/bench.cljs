(ns cljs.lean-map.bench
  (:require [cljs.lean-map.core :as lean]))

(def println print)

(set! *print-fn* js/print)

(def small-map-size 100)
(def small-map-sample 2000)

(def medium-map-size 4000)
(def medium-map-sample 50)

(def large-map-size 10000)
(def large-map-sample 20)

(def size->sample
  {small-map-size small-map-sample
   medium-map-size medium-map-sample
   large-map-size large-map-sample})

(declare test-keys)

(defn sized-map [m n]
  (loop [m m i 0]
    (if (< i n)
      (recur (assoc m (get test-keys i) i) (inc i))
      m)))

(defmulti map-bench
  (fn [benchmark _ _] benchmark))

(defmethod map-bench :assoc [_ m _]
  (println "Assoc")
  (doseq [[size sample] size->sample]
    (simple-benchmark [] (sized-map m size) sample)))

(defmethod map-bench :dissoc [_ _ {:keys [small-map medium-map large-map]}]
  (println "Dissoc")
  (let [maps [small-map medium-map large-map]
        samples [small-map-sample medium-map-sample large-map-sample]]
    (doseq [[m sample] (partition 2 (interleave maps samples))]
     (simple-benchmark
       []
       (loop [m m i (count m)]
         (when (> i 0)
           (recur (dissoc m (get test-keys i)) (dec i))))
       sample))))

(defmethod map-bench :dissoc-fail [_ _ {:keys [small-map medium-map large-map]}]
  (println "Dissoc Fail")
  (let [maps [small-map medium-map large-map]
        samples [small-map-sample medium-map-sample large-map-sample]]
    (doseq [[m sample] (partition 2 (interleave maps samples))]
      (simple-benchmark
        []
        (loop [m m i (count m)]
          (when (> i 0)
            (recur (dissoc m i) (dec i))))
        sample))))

(defmethod map-bench :hash [_ _ {:keys [small-map medium-map large-map]}]
  (println "Hash")
  (let [maps [small-map medium-map large-map]
        samples [small-map-sample medium-map-sample large-map-sample]]
    (doseq [[m sample] (partition 2 (interleave maps samples))]
      (simple-benchmark [] (hash-unordered-coll m) sample))))

(defmethod map-bench :equals [_ _ {:keys [small-map medium-map large-map]}]
  (println "Equals")
  (let [maps [small-map medium-map large-map]
        samples [small-map-sample medium-map-sample large-map-sample]]
    (doseq [[m sample] (partition 2 (interleave maps samples))]
      (let [step (/ (count m) 20)
            cm   (loop [cm m i 0]
                   (if (< i 20)
                     (recur (assoc (dissoc cm (get test-keys i)) (get test-keys i) i) (+ i step))
                     cm))]
        (simple-benchmark [] (= m cm) sample)))))

(defmethod map-bench :equals-fail [_ _ {:keys [small-map medium-map large-map]}]
  (println "Equals fail")
  (let [maps [small-map medium-map large-map]
        samples [small-map-sample medium-map-sample large-map-sample]]
    (doseq [[m sample] (partition 2 (interleave maps samples))]
      (let [step (/ (count m) 20)
            neq-m (assoc m (get test-keys step) -100)]
        (simple-benchmark [] (= m neq-m) sample)))))

(defmethod map-bench :worst-equals [_ _ {:keys [small-map medium-map large-map]}]
  (println "Worst Case Equals (same map different location in memory)")
  (let [maps [small-map medium-map large-map]
        samples [small-map-sample medium-map-sample large-map-sample]]
    (doseq [[m sample] (partition 2 (interleave maps samples))]
      (let [cm (into (empty m) m)]
        (simple-benchmark [] (= m cm) sample)))))

(defmethod map-bench :sequence [_ _ {:keys [small-map medium-map large-map]}]
  (println "sequence")
  (let [maps [small-map medium-map large-map]
        samples [small-map-sample medium-map-sample large-map-sample]]
    (doseq [[m sample] (partition 2 (interleave maps samples))]
      (simple-benchmark
        []
        (loop [s (seq m)]
          (if (seq s)
            (recur (next s))))
        sample))))

(defmethod map-bench :reduce [_ _ {:keys [small-map medium-map large-map]}]
  (println "Reduce")
  (let [maps [small-map medium-map large-map]
        samples [small-map-sample medium-map-sample large-map-sample]]
    (doseq [[m sample] (partition 2 (interleave maps samples))]
      (simple-benchmark [] (reduce (fn [sum [k v]] (+ 1 sum)) 0 m) sample))))

(defn run-benchmarks [benchmarks empty-map data]
  (println "Running Benchmarks")
  (doseq [benchmark benchmarks]
    (map-bench benchmark empty-map data)))

(def lem (.-EMPTY lean/PersistentHashMap))
(def cem (.-EMPTY cljs.core/PersistentHashMap))

(def test-keys (mapv (fn [i]
                       (let [nk (keyword (str "key" i))]
                         (hash nk)
                         nk))
                     (range large-map-size)))

(println "Current Maps")
(let [benchmarks  [:assoc :dissoc :dissoc-fail :hash :equals :equals-fail :worst-equals :sequence :reduce]
      empty-map cem
      data {:small-map (sized-map empty-map small-map-size)
            :medium-map (sized-map empty-map medium-map-size)
            :large-map (sized-map empty-map large-map-size)}]
  (run-benchmarks benchmarks empty-map data))

(println "Lean Maps")
(let [benchmarks  [:assoc :dissoc :dissoc-fail :hash :equals :equals-fail :worst-equals :sequence :reduce]
      empty-map lem
      data {:small-map (sized-map empty-map small-map-size)
            :medium-map (sized-map empty-map medium-map-size)
            :large-map (sized-map empty-map large-map-size)}]
  (run-benchmarks benchmarks empty-map data))

(comment
  (let [benchmarks  [:assoc :dissoc :dissoc-fail :hash :equals :equals-fail :sequence :reduce]
        empty-map (empty {})
        data {:small-map (sized-map empty-map small-map-size)
              :medium-map (sized-map empty-map medium-map-size)
              :large-map (sized-map empty-map large-map-size)}]
    (run-benchmarks benchmarks empty-map data)))
