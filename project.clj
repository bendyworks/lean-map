(defproject lean-map "0.2.1"
  :description "Lean Hash Array Mapped Trie implementation in ClojureScript"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :clean-targets ^{:protect false} ["resources/out"]

  :jvm-opts ^:replace ["-Xms512m" "-Xmx512m" "-server"]

  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.170"]]

  :profiles {:test {:dependencies [[collection-check "0.1.7-SNAPSHOT"]]}}

  :plugins [[lein-doo "0.1.6-rc.1"]]

  :cljsbuild {:builds
              [{:id "test"
                :source-paths ["src/main" "test"]
                :compiler {:output-to "resources/public/js/testable.js"
                           :main cljs.lean-map.test.runner
                           :optimizations :none}}
               {:id "node-test"
                :source-paths ["src/main" "test"]
                :compiler {:output-to "resources/public/js/testable.js"
                           :main cljs.lean-map.test.runner
                           :output-dir "target"
                           :target :nodejs
                           :optimizations :none}}]})