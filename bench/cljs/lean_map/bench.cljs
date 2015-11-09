(ns cljs.lean-map.bench)

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

(defn sized-map [m n]
  (loop [m m i 0]
    (if (< i n)
      (recur (assoc m (str "key" i) i) (inc i))
      m)))

(defmulti map-bench
  (fn [benchmark _ _] benchmark))

(defmethod map-bench :assoc [_ m _]
  (doseq [[size sample] size->sample]
    (simple-benchmark [] (sized-map m size) sample)))

(defmethod map-bench :dissoc [_ _ {:keys [small-map medium-map large-map]}]
  (let [maps [small-map medium-map large-map]
        samples [small-map-sample medium-map-sample large-map-sample]]
    (doseq [[m sample] (partition 2 (interleave maps samples))]
     (simple-benchmark
       []
       (loop [m m i (count m)]
         (when (> i 0)
           (recur (dissoc m (str "key" i)) (dec i))))
       sample))))

(defmethod map-bench :dissoc-fail [_ _ {:keys [small-map medium-map large-map]}]
  (let [maps [small-map medium-map large-map]
        samples [small-map-sample medium-map-sample large-map-sample]]
    (doseq [[m sample] (partition 2 (interleave maps samples))]
      (simple-benchmark
        []
        (loop [m m i (count m)]
          (when (> i 0)
            (recur (dissoc m (str "not-a-key" i)) (dec i))))
        sample))))

(defmethod map-bench :hash [_ _ {:keys [small-map medium-map large-map]}]
  (let [maps [small-map medium-map large-map]
        samples [small-map-sample medium-map-sample large-map-sample]]
    (doseq [[m sample] (partition 2 (interleave maps samples))]
      (simple-benchmark [] (hash-unordered-coll m) sample))))

(defmethod map-bench :equals [_ _ {:keys [small-map medium-map large-map]}]
  (let [maps [small-map medium-map large-map]
        samples [small-map-sample medium-map-sample large-map-sample]]
    (doseq [[m sample] (partition 2 (interleave maps samples))]
      (let [cm (clone m)]
        (simple-benchmark [] (= m cm) sample)))))

(defmethod map-bench :equals-fail [_ _ {:keys [small-map medium-map large-map]}]
  (let [maps [small-map medium-map large-map]
        samples [small-map-sample medium-map-sample large-map-sample]]
    (doseq [[m sample] (partition 2 (interleave maps samples))]
      (let [neq-m (assoc m (js-obj) 0)]
        (simple-benchmark [] (= m neq-m) sample)))))

(defmethod map-bench :sequence [_ _ {:keys [small-map medium-map large-map]}]
  (let [maps [small-map medium-map large-map]
        samples [small-map-sample medium-map-sample large-map-sample]]
    (doseq [[m sample] (partition 2 (interleave maps samples))]
      (simple-benchmark [] (doseq [[k v] m] [v k]) sample))))

(defmethod map-bench :reduce [_ _ {:keys [small-map medium-map large-map]}]
  (let [maps [small-map medium-map large-map]
        samples [small-map-sample medium-map-sample large-map-sample]]
    (doseq [[m sample] (partition 2 (interleave maps samples))]
      (simple-benchmark [] (reduce (fn [sum [k v]] (+ 1 sum)) 0 m) sample))))

(defn run-benchmarks [benchmarks empty-map data]
  (doseq [benchmark benchmarks]
    (map-bench benchmark empty-map data)))

(comment
  (let [benchmarks  [:assoc :dissoc :dissoc-fail :hash :equals :equals-fail :sequence :reduce]
        empty-map (empty {})
        data {:small-map (sized-map empty-map small-map-size)
              :medium-map (sized-map empty-map medium-map-size)
              :large-map (sized-map empty-map large-map-size)}]
    (run-benchmarks benchmarks empty-map data)))
