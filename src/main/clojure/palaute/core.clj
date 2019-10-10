(ns palaute.core
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [compojure.core :refer [GET POST defroutes context]]
            [compojure.handler :refer [site]]
            [compojure.route :refer [resources files not-found]]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.util.response :refer [response]]
            [taoensso.timbre :refer [info warn error]]
            [ring.util.request :refer [body-string]]
            [clojure.edn :as edn]
            [environ.core :refer [env]]
            [ring.util.http-response :refer [ok created]]
            [camel-snake-kebab.core :refer [->snake_case ->kebab-case-keyword ->camelCase]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [palaute.index :refer [index]]
            [compojure.api.sweet :as api]
            [schema.core :as s]
            [schema.coerce :as c]
            [palaute.flyway :refer [migrate]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [yesql.core :as sql]
            [ring.middleware.not-modified :refer [wrap-not-modified]])
  (:gen-class))

(sql/defqueries "sql/palaute.sql")

(s/defschema Feedback
  {:feedback   s/Str
   :key        s/Str
   :stars      (s/pred #(<= 1 % 5))
   :user-agent s/Str
   :data       (s/maybe s/Any)})

(defn feedback->row [feedback]
  (let [joda->timestamp (fn [a]
                          (assoc (vec a) 0 (.getMillis (first a))))]
    (-> feedback
        (select-keys [:created_time :stars :user_agent :feedback])
        vals
        joda->timestamp)))

(api/defroutes app-routes
  (api/context
   "/palaute" []
   (api/GET "/health_check" [] (ok))
   (api/context
    "/api" []
    (api/GET
     "/keskiarvo" []
     :query-params [{q :- s/Str nil}]
     (ok
      (first (palaute.db/exec yesql-get-average {:key q}))))
    (api/GET
     "/palaute" []
     :query-params [{q :- s/Str nil}]
     (ok
      (doall
       (map feedback->row
            (palaute.db/exec yesql-get-feedback {:key q})))))
    (api/POST
     "/palaute" []
     :body [feedback Feedback]
     (palaute.db/exec yesql-insert-feedback<!
                      (->> feedback
                           (transform-keys ->snake_case)))
     (created)))
   (api/GET "/" [] index)
   (resources "/" {:root "static"})))

(def handler
  (api/api
   app-routes))

(defn -main
  []
  (let [config (edn/read-string (slurp (get env :palaute-config "config/config.edn")))
        db     (:db config)
        port   (or (:port config)
                   (Integer/parseInt (get env :palaute-http-port "8080")))]
    (palaute.db/set-datasource db)
    (migrate db)
    (run-jetty (wrap-reload #'handler) {:port port})))
