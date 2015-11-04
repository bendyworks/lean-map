(defproject fast-map "0.1.0-SNAPSHOT"
  :description "Lean Hash Array Mapped Trie implementation in ClojureScript"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :clean-targets ^{:protect false} ["resources/out"]

  :jvm-opts ^:replace ["-Xms512m" "-Xmx512m" "-server"]

  :plugins [[lein-cljsbuild "1.1.0"]]

  :cljsbuild {:builds
              [{:id "test"
                :source-paths ["src" "test"]
                :compiler {:output-to "resources/out/tests.simple.js"
                           :output-dir "resources/out"
                           :source-map "resources/out/tests.simple.js.map"
                           :output-wrapper false
                           :static-fns true
                           :optimizations :simple}}]}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "1.7.145"]
                 [org.clojure/test.check "0.8.2"]
                 [com.gfredericks/test.chuck "0.2.0"]])