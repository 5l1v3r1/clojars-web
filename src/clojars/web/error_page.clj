(ns clojars.web.error-page
  (:require [clojars.web.common :refer [html-doc]]
            [ring.util.response :refer [response status content-type]]
            [hiccup.page-helpers :refer [link-to]]))

(defn error-page-response [throwable]
  (-> (response (html-doc nil
                 "Oops, we encountered an error"
                 [:h1 "Oops!"]
                 [:p
                  "It seems as if an internal system error has occurred. Please give it another try. If it still doesn't work please "
                  (link-to "https://github.com/ato/clojars-web/issues" "open an issue.")]))
      (status 500)
      (content-type "text/html")))

(defn wrap-exceptions [app]
  (fn [req]
    (try
      (app req)
      (catch Throwable t
        (println (str "A server error has occured: " (.getMessage t)))
        (.printStackTrace t)
        (error-page-response t)))))
