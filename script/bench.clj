(require '[cljs.build.api :as b])

(b/build (b/inputs "src/main" "src/bench")
         {:asset-path "bench"
          :output-to "resources/bench/app.js"
          :output-dir "resources/bench"
          :optimizations :advanced
          :verbose true})
