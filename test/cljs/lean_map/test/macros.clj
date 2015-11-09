(ns cljs.lean-map.test.macros
  (:require [cljs.lean-map.test.utils :as utils]))

(defmacro reporting-failing-actions [& body]
  `(let [old-report-fn# ~'cljs.test/report]
     (binding [cljs.test/report #(do (old-report-fn# %)
                                     (cljs.lean-map.test.utils/report-failing-actions %))]
       ~@body)))
