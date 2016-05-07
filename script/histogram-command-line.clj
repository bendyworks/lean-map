(require '[cljs.build.api :as b])

(b/build (b/inputs "src/main" "src/histogram")
         {:asset-path "histogram"
          :output-to "resources/histogram/command-line.js"
          :output-dir "resources/histogram"
          :closure-defines {'cljs.lean-map.histogram.command-line.run true}
          :optimizations :advanced
          :verbose true})
