(require '[cljs.build.api :as b])

(b/build (b/inputs "src/main" "src/histogram/cljs/lean_map/histogram/base.cljs" "src/histogram/cljs/lean_map/histogram/chart.cljs")
         {:asset-path "histogram"
          :output-to "resources/histogram/chart.js"
          :output-dir "resources/histogram"
          :optimizations :advanced
          :source-map "resources/histogram/chart.js.map"
          :verbose true})
