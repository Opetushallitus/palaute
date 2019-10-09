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

(def FeedbackEnforcer (c/coercer! Feedback c/json-coercion-matcher))

(defn feedback->row [feedback]
  (let [joda->timestamp (fn [a]
                          (assoc (vec a) 0 (.getMillis (first a))))]
    (-> feedback
        (select-keys [:created_time :stars :user_agent :feedback])
        vals
        joda->timestamp)))

(defroutes app
  (context "/palaute" []
           (GET "/health_check" []
                (ok))
           (context "/api" []
                    (GET "/palaute" request
                         (let [key       (-> request :params :q)
                               feedbacks (palaute.db/exec yesql-get-feedback {:key key})]
                           (ok
                            (map feedback->row feedbacks))))
                    (POST "/palaute" request
                          (let [feedback (->> (FeedbackEnforcer (:body request))
                                              (transform-keys ->snake_case))]
                            (palaute.db/exec yesql-insert-feedback<! feedback)
                            (created))))
           (GET "/" [] index)
           (resources "/" {:root "static"})))

(def handler
  (-> (site app)
      (wrap-json-response)
      (wrap-json-body {:keywords? true})))

(defn -main
  []
  (let [config (edn/read-string (slurp (get env :palaute-config "config/config.edn")))
        db     (:db config)
        port   (or (:port config)
                   (Integer/parseInt (get env :palaute-http-port "8080")))]
    (palaute.db/set-datasource db)
    (migrate db)
    (run-jetty (wrap-reload #'handler) {:port port})))
