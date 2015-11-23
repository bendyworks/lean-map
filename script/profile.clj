(require '[cljs.build.api :as b])

(b/build (b/inputs "src/main" "src/profile")
         {:asset-path "profile"
          :output-to "resources/profile/app.js"
          :output-dir "resources/profile"
          :optimizations :simple
          :static-fns true
          :pretty-print true
          :source-map "resources/profile/app.js.map"
          :verbose true})
