(ns palaute.core
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [compojure.core :refer [GET POST defroutes routes context]]
            [compojure.handler :refer [site]]
            [ring.swagger.coerce :as coerce]
            [compojure.api.middleware
             :refer
             [api-middleware-defaults default-coercion-matchers]]
            [medley.core :refer [map-kv]]
            [org.httpkit.client :as http]
            [palaute.palaute-schema :refer [Feedback formatter zone-id json-schema-coercion-matcher]]
            [compojure.route :refer [resources files not-found]]
            [compojure.api.sweet :as api]
            [ring.middleware.reload :refer [wrap-reload]]
            [ring.middleware.logger :refer [wrap-with-logger] :as middleware-logger]
            [ring.util.response :refer [response]]
            [cheshire.generate :refer [add-encoder]]
            [taoensso.timbre :as log]
            [ring.util.request :refer [body-string]]
            [palaute.authentication.cas-client :refer [new-cas-client]]
            [palaute.db :refer [exec]]
            [clj-time.format :as tf]
            [palaute.timbre-config :refer [configure-logging!]]
            [ring.util.http-response :refer [ok created]]
            [palaute.sqs :as sqs]
            [camel-snake-kebab.core :refer [->snake_case ->kebab-case-keyword ->camelCase]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [palaute.index :refer [index]]
            [palaute.log.audit-log :as audit-log]
            [schema.core :as s]
            [clojure.string :as string]
            [ring.middleware.session :as ring-session]
            [schema.coerce :as c]
            [clj-time.coerce :as tc]
            [environ.core :refer [env]]
            [palaute.authentication.session-store :refer [create-store]]
            [palaute.log.access-log :as access-log]
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
  (:import java.util.Locale
           java.time.ZonedDateTime
           org.joda.time.DateTime
           java.util.Date
           org.joda.time.DateTimeZone
           org.joda.time.format.DateTimeFormat
           org.joda.time.format.ISODateTimeFormat
           org.joda.time.format.DateTimeFormatterBuilder
           java.time.format.DateTimeFormatter)
  (:gen-class))

(sql/defqueries "sql/palaute.sql")

(add-encoder DateTime
             (fn [d json-generator]
               (.writeString
                 json-generator
                 (.print formatter (.withZone d zone-id)))))

(defn feedback->row [feedback]
  (let [joda->timestamp (fn [a]
                            (if (first a)
                              (assoc (vec a) 0 (.getMillis (first a)))
                              (assoc (vec a) 0 1572506204343)))]
    (-> feedback
        (select-keys [:created_time :stars :user_agent :feedback])
        vals
        joda->timestamp)))

(defn- wrap-database-backed-session [handler]
  (ring-session/wrap-session handler
                             {:root         "/palaute"
                              :cookie-attrs {:secure (not (-> config :dev))}
                              :store        (create-store)}))

(defn wrap-session-client-headers [handler]
  (fn [{:keys [headers] :as req}]
    (let [user-agent (get headers "user-agent")
          client-ip  (or (get headers "x-real-ip")
                         (get headers "x-forwarded-for"))]
      (handler
       (-> req
           (assoc-in [:session :user-agent] user-agent)
           (assoc-in [:session :client-ip] client-ip))))))

(defn- proxy-request [service-path request]
  (let [prefix   (str "https://" (get-in config [:urls :virkailija-host]) service-path)
        path     (-> request :params :*)
        response @(http/get (str prefix path) {:headers (dissoc (:headers request) "host")})]
    (assoc
     response
     ;; http-kit returns header names as keywords, but Ring requires strings :(
     :headers (map-kv
               (fn [header-kw header-value] [(name header-kw) header-value])
               (:headers request)))))

(api/defroutes local-raami-routes
  (api/undocumented
   (api/GET "/virkailija-raamit/*" request
            :query-params [{fingerprint :- [s/Str] nil}]
            (proxy-request "/virkailija-raamit/" request))
   (api/GET "/authentication-service/*" request
            (proxy-request "/authentication-service/" request))
   (api/GET "/cas/*" request
            (proxy-request "/cas/" request))
   (api/GET "/lokalisointi/*" request
            (proxy-request "/lokalisointi/" request))))

(api/defroutes app-routes
  (api/context
   "/api" []
   :coercion
   (constantly
    (merge
     default-coercion-matchers
     {:body json-schema-coercion-matcher}))
   (api/GET
    "/keskiarvo" {session :session}
    :query-params [{q :- s/Str nil}]
    (ok
     (first (exec yesql-get-average {:key q}))))
   (api/GET
    "/palaute" {session :session}
    :query-params [{q :- s/Str nil}]
    (audit-log/log {:new       {:q q}
                    :id        {:q q}
                    :session   session
                    :operation audit-log/operation-read})
    (ok
     (doall
      (map feedback->row
           (exec yesql-get-feedback {:key q})))))
   (api/POST
    "/palaute" {session :session}
    :body [feedback Feedback]
    (audit-log/log {:new       feedback
                    :id        {:key (:key feedback)}
                    :session   session
                    :operation audit-log/operation-new})
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
  (->
    (api/api
     (when (-> config :dev)
       local-raami-routes)

     (api/context
      "/palaute" []
      (api/GET "/health_check" [] (ok))
      (api/undocumented

       (->
        (api/middleware
         [with-authentication]
         app-routes)
        (wrap-database-backed-session)
        (wrap-session-client-headers))
       (-> auth-routes
           (wrap-database-backed-session)
           (wrap-session-client-headers)))
      (api/GET "/" [] index)
      (resources "/" {:root "static"})))
    (wrap-with-logger
     :debug      identity
     :info       (fn [x] (access-log/info x))
     :warn       (fn [x] (access-log/warn x))
     :error      (fn [x] (access-log/error x))
     :pre-logger (fn [_ _] nil)
     :post-logger
                 (fn [options {:keys [uri] :as request} {:keys [status] :as response} totaltime]
                   (when
                     (or
                      (>= status 400)
                      (clojure.string/starts-with? uri "/palaute/api/"))
                     (access-log/log options request response totaltime))))))

(defn -main
  []
  (configure-logging!)
  (sqs/unload-sqs-queue)
  (log/info "Server started!")
  (let [db     (:db config)
        port   (or (:port config)
                   (Integer/parseInt (get env :palaute-http-port "8080")))]
    (palaute.db/set-datasource db)
    (migrate db)
    (run-jetty (wrap-reload #'handler) {:port port})))
