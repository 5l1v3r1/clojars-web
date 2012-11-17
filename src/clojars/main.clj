(ns clojars.main
  (:require [clojars.scp]
            [ring.adapter.jetty :refer [run-jetty]]
            [clojars.web :refer [clojars-app]]
            [clojars.promote :as promote]
            [clojars.config :refer [config configure]]
            [clojure.tools.nrepl.server :as nrepl])
  (:import com.martiansoftware.nailgun.NGServer
           java.net.InetAddress)
  (:gen-class))

(defn start-jetty []
  (when-let [port (:port config)]
    (println "clojars-web: starting jetty on" (str "http://" (:bind config) ":" port))
    (run-jetty #'clojars-app {:host (:bind config)
                            :port port
                            :join? false})))

(defn start-nailgun []
  (when-let [port (:nailgun-port config)]
    (println "clojars-web: starting nailgun on" (str (:nailgun-bind config) ":" port))
    (.run (NGServer. (InetAddress/getByName (:nailgun-bin config)) port))))

(defn -main [& args]
  (configure args)
  (promote/start)
  (start-jetty)
  (start-nailgun)
  (when-let [port (config :nrepl-port)]
    (nrepl/start-server :port port)))

; (defonce server (run-jetty #'clojars-app {:port 8080 :join? false}))