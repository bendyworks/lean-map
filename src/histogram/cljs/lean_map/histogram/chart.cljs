(ns cljs.lean-map.histogram.chart
  (:require
    [cljs.lean-map.histogram.base :as hb]
    [goog.dom :as dom]
    [goog.dom.forms :as form]
    [cljsjs.c3]))
;;UI Functions
(defn get-form-map [form-name]
  (form/getFormDataMap (dom/getElement form-name)))

(defn goog-map->cljs-map [goog-map]
  (reduce
    (fn [m k]
      (assoc m (keyword k) (.get goog-map k)))
    {}
    (.getKeys goog-map)))

(defn user-input [user-map]
  (let [log-histogram (contains? user-map :log)
        size (-> (user-map :size) (aget 0) js/parseInt)
        times (-> (user-map :times) (aget 0) js/parseInt)]
    {:log  log-histogram
     :size (if (js/isNaN size) 10 size)
     :times (if (js/isNaN times) 2000 times)}))

;;Chart functions
(defn get-chart-id [check op]
  (str (name check) "-" (name op)))

(defn create-chart-data [id percents current lean]
  #js {:data
               #js {:columns #js [(to-array (into ["current"] current))
                                  (to-array (into ["lean"] lean))]
                    :types   #js {"current" "area" "lean" "area"}}
       :axis   #js {:x
                    #js {:type       "category"
                         :categories (to-array percents)}}
       :zoom   #js {:enabled true
                    :rescale true}
       :bindto (str "#" id)})
(defn flatten-histogram [histogram]
  (let [ops (disj (into #{} (keys histogram)) :assoc-size :repeat-times)
        checks (keys (get histogram (first ops)))
        mapping (for [op ops check checks] [(get-chart-id check op) check op])]
    (reduce (fn [m [id check op]]
              (assoc-in m [id (namespace check)] (get-in histogram [op check])))
            {}
            mapping)))

(def percents (conj (into ["minimum"] (map #(str % "%") hb/histogram-percents)) "maximum"))

(defn histogram-chart-data [histogram]
  (map (fn [[id hc]] (create-chart-data id percents (hc "current") (hc "lean"))) histogram))

(defn draw-charts! [histograms-charts ^:boolean log]
  (doseq [chart histograms-charts]
    (when log
      (js/console.log chart))
    (js/c3.generate chart)))

(defn ^:export show-histogram
  ([size times] (show-histogram size times false))
  ([size times ^:boolean log]
   (-> (hb/get-histogram-data size times)
       flatten-histogram
       histogram-chart-data
       (draw-charts! log))))

(defn ^:export chart-histogram []
  (let [{size :size times :times log :log} (-> (get-form-map "map") goog-map->cljs-map user-input)]
    (show-histogram size times log)))