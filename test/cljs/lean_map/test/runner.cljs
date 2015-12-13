(ns cljs.lean-map.test.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [cljs.lean-map.test.core]
            [cljs.lean-map.test.util]
            [cljs.lean-map.test.cljs-maplike]
            [cljs.lean-map.test.lean-maplike]))

(doo-tests 'cljs.lean-map.test.core 'cljs.lean-map.test.util 'cljs.lean-map.test.cljs-maplike 'cljs.lean-map.test.lean-maplike)
