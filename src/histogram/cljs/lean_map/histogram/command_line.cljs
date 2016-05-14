(ns cljs.lean-map.histogram.command-line
  (:require [cljs.lean-map.histogram.base :as hb]
            [cljs.pprint :as pp]))

(def argc
  (if (exists? (js* "scriptArgs"))
    (js* "scriptArgs")
    (js* "arguments")))

(def size (or (js/parseInt(aget argc 0)) 10))
(def times (or (js/parseInt (aget argc 1)) 1000))

(set! *print-fn* js/print)

(def percents (conj (into [0] hb/histogram-percents) 100))
(goog-define run false)

(defn show-percents [histograms]
  (into {} (map (fn [[histogram percentages]] [histogram (into (sorted-map) (zipmap percents percentages))]) histograms)))

(if run
  (let [histograms (hb/get-histogram-data size times)]
    (pp/pprint
      (into
        {}
        (-> histograms
            (update :op show-percents)
            (update :total show-percents))))))

