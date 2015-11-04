(ns cljs.fast-map.test.macros
  (:require [cljs.fast-map.test.utils :as utils]))

(defmacro reporting-failing-actions [& body]
  `(let [old-report-fn# ~'cljs.test/report]
     (binding [cljs.test/report #(do (old-report-fn# %)
                                     (cljs.fast-map.test.utils/report-failing-actions %))]
       ~@body)))