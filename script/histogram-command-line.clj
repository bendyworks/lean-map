(require '[cljs.build.api :as b])

(b/build (b/inputs "src/main" "src/histogram/cljs/lean_map/histogram/base.cljs" "src/histogram/cljs/lean_map/histogram/command_line.cljs")
         {:asset-path "histogram"
          :output-to "resources/histogram/command-line.js"
          :output-dir "resources/histogram"
          :optimizations :advanced
          :verbose true})
