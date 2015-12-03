(ns cljs.lean-map.test.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [cljs.lean-map.test.core]))

(doo-tests 'cljs.lean-map.test.core)
