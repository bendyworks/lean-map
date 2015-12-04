(ns cljs.lean-map.test.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [cljs.lean-map.test.core]
            [cljs.lean-map.test.util]))

(doo-tests 'cljs.lean-map.test.util 'cljs.lean-map.test.core)
