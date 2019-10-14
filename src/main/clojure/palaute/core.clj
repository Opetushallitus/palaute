(ns palaute.core
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [compojure.core :refer [GET POST defroutes routes context]]
            [compojure.handler :refer [site]]
            [compojure.route :refer [resources files not-found]]
            [compojure.api.sweet :as api]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.util.response :refer [response]]
            [taoensso.timbre :as log]
            [ring.util.request :refer [body-string]]
            [palaute.authentication.cas-client :refer [new-cas-client]]
            [palaute.db :refer [exec]]
            [palaute.timbre-config :refer [configure-logging!]]
            [ring.util.http-response :refer [ok created]]
            [camel-snake-kebab.core :refer [->snake_case ->kebab-case-keyword ->camelCase]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [palaute.index :refer [index]]
            [schema.core :as s]
            [clojure.string :as string]
            [ring.middleware.session :as ring-session]
            [schema.coerce :as c]
            [environ.core :refer [env]]
            [palaute.authentication.session-store :refer [create-store]]
            [palaute.config :refer [config]]
            [palaute.flyway :refer [migrate]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [yesql.core :as sql]
            [palaute.authentication.auth
             :refer
             [cas-login login cas-initiated-logout logout]]
            [palaute.authentication.auth-middleware :refer [with-authentication]]
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

(defn- wrap-database-backed-session [handler]
    (ring-session/wrap-session handler
                               {:root         "/palaute"
                                :cookie-attrs {:secure (not (-> config :dev))}
                                :store        (create-store)
                                }))

(defn wrap-session-client-headers [handler]
  [handler]
  (fn [{:keys [headers] :as req}]
    (let [user-agent (get headers "user-agent")
          client-ip  (or (get headers "x-real-ip")
                         (get headers "x-forwarded-for"))]
      (handler
       (-> req
           (assoc-in [:session :user-agent] user-agent)
           (assoc-in [:session :client-ip] client-ip))))))

(api/defroutes app-routes
  (api/context
   "/api" []
   (api/GET
    "/keskiarvo" []
    :query-params [{q :- s/Str nil}]
    (ok
     (first (exec yesql-get-average {:key q}))))
   (api/GET
    "/palaute" {session :session}
    :query-params [{q :- s/Str nil}]
    (prn session)
    (ok
     (doall
      (map feedback->row
           (exec yesql-get-feedback {:key q})))))
   (api/POST
    "/palaute" []
    :body [feedback Feedback]
    (exec yesql-insert-feedback<!
          (->> feedback
               (transform-keys ->snake_case)))
    (created))))

(defn- rewrite-url-for-environment
  [url-from-session]
  (if (-> config :dev)
    url-from-session
    (string/replace url-from-session #"^http://" "https://")))

(defn- fake-login-provider [ticket]
  (fn []
    (let [username      "1.2.246.562.11.11111111111"
          unique-ticket (str (System/currentTimeMillis) "-" (rand-int (Integer/MAX_VALUE)))]
      [username unique-ticket])))

(defonce cas-client (new-cas-client))

(api/defroutes auth-routes
  (api/context
   "/auth" []
   (api/undocumented
    (api/GET
     "/cas" [ticket :as request]
     (let [redirect-url   (if-let [url-from-session (get-in request [:session :original-url])]
                            (rewrite-url-for-environment url-from-session)
                            (get-in config [:public-config :service_url]))
           login-provider (if (-> config :dev)
                            (fake-login-provider ticket)
                            (cas-login cas-client ticket))]
       (login login-provider
              redirect-url
              (:session request))))
    (api/POST "/cas" [logoutRequest]
              (cas-initiated-logout logoutRequest))
    (api/GET "/logout" {session :session}
             (logout session)))))

(def handler
  (api/api
   (api/context
    "/palaute" []
    (api/GET "/health_check" [] (ok))
    (api/undocumented
     (-> (api/middleware
          [with-authentication]
          app-routes)
         (wrap-database-backed-session)
         (wrap-session-client-headers))
     (-> auth-routes
         (wrap-database-backed-session)
         (wrap-session-client-headers)))
    (api/GET "/" [] index)
    (resources "/" {:root "static"}))))

(defn -main
  []
  (configure-logging!)
  (log/info "Server started!")
  (let [db     (:db config)
        port   (or (:port config)
                   (Integer/parseInt (get env :palaute-http-port "8080")))]
    (palaute.db/set-datasource db)
    (migrate db)
    (run-jetty (wrap-reload #'handler) {:port port})))
