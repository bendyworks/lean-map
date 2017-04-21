(defproject lean-map "0.4.0"
  :description "Lean Hash Array Mapped Trie implementation in ClojureScript"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :clean-targets ^{:protect false} ["resources/out"]

  :source-paths  ["src/main"]
  :java-source-paths ["src/main/java"]

  :jvm-opts ^:replace ["-Xmx2g" "-server"]

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.9.521"]
                 [org.clojure/test.check "0.9.0"]
                 [com.gfredericks/test.chuck "0.2.7"]]

  :profiles {:clj {:source-paths ["dev"]}
             :clj-test {:source-paths ["test"]
                        :dependencies [[collection-check "0.1.7"]]}
             :test {:dependencies [[collection-check "0.1.7"]]}
             :histogram {:dependencies [[cljsjs/c3 "0.4.10-0"]]}}

  :plugins [[lein-doo "0.1.6"]]

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