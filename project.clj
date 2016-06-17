(defproject lean-map "0.4.0-SNAPSHOT"
  :description "Lean Hash Array Mapped Trie implementation in ClojureScript"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :clean-targets ^{:protect false} ["resources/out"]

  :source-paths  ["src/main"]
  :java-source-paths ["src/main/java"]

  :jvm-opts ^:replace ["-Xms512m" "-Xmx512m" "-server"]

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.8.51"]]

  :profiles {:clj {:source-paths ["dev"]
                   :dependencies [[im.chit/vinyasa "0.4.3"]
                                  [leiningen #=(leiningen.core.main/leiningen-version)]]
                   :injections [(require '[vinyasa.inject :as inject])
                                (inject/in
                                  [vinyasa.inject :refer [inject [in inject-in]]]
                                  [vinyasa.lein :exclude [*project*]])]}
             :clj-test {:source-paths ["test"]
                        :dependencies [[org.clojure/test.check "0.9.0"]
                                       [collection-check "0.1.6"]]}
             :test {:dependencies [[collection-check "0.1.7-SNAPSHOT"]]}
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