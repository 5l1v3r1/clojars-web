(defproject clojars-web "42"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.2.1"]
                 [yeller-clojure-client "1.4.1"]
                 [org.apache.maven/maven-model "3.0.4"
                  :exclusions
                  [org.codehaus.plexus/plexus-utils]]
                 [com.cemerick/pomegranate "0.3.0"
                  :exclusions
                  [org.apache.httpcomponents/httpcore
                   commons-logging]]
                 [compojure "1.3.3"]
                 [ring-middleware-format "0.5.0"]
                 [hiccup "1.0.3"]
                 [cheshire "5.4.0"]
                 [org.xerial/sqlite-jdbc "3.8.11.2"]
                 [org.apache.jclouds/jclouds-all "1.9.2"]
                 [org.clojure/tools.logging "0.3.1"] ;; required by jclouds
                 [org.apache.commons/commons-email "1.2"]
                 [commons-codec "1.6"]
                 [net.cgrand/regex "1.0.1"
                  :exclusions [org.clojure/clojure]]
                 [clj-time "0.9.0"]
                 [com.cemerick/friend "0.2.1"
                  :exclusions [org.clojure/core.cache
                               org.apache.httpcomponents/httpclient
                               org.apache.httpcomponents/httpcore
                               commons-logging
                               com.google.inject/guice]]
                 [clj-stacktrace "0.2.6"]
                 [ring-anti-forgery "0.2.1"]
                 [valip "0.2.0" :exclusions [commons-logging]]
                 [clucy "0.3.0"]
                 [org.clojure/tools.nrepl "0.2.11"]
                 [yesql "0.5.1"]
                 [com.stuartsierra/component "0.2.3"]
                 [duct/hikaricp-component "0.1.0"]
                 [duct "0.4.4"]
                 [meta-merge "0.1.1"]
                 [ring-jetty-component "0.3.0"]
                 [digest "1.4.4"]]
  :plugins [[supersport "1"]]
  :main ^:skip-aot clojars.main
  :target-path "target/%s/"
  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "super.sport/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag"]
                  ["change" "version" "super.sport/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]
  :aliases {"migrate" ["run" "-m" "clojars.tools.migrate-db"]}
  :profiles
  {:dev  [:project/dev  :profiles/dev]
   :test [:project/test :profiles/test]
   :uberjar {:aot :all}
   :profiles/dev  {}
   :profiles/test {}
   :project/dev   {:source-paths ["dev"]
                   :repl-options {:init-ns user}
                   :dependencies [[reloaded.repl "0.2.0"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [eftest "0.1.0"]
                                  [kerodon "0.7.0"
                                   :exclusions [org.apache.httpcomponents/httpcore]]
                                  [clj-http "2.2.0"]
                                  [com.google.jimfs/jimfs "1.0"]
                                  [net.polyc0l0r/bote "0.1.0"
                                   :exclusions [org.clojars.kjw/slf4j-simple]]]
                   :resource-paths ["local-resources"]}
   :project/test  {}})
