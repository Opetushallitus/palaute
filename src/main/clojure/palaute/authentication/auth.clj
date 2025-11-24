(ns palaute.authentication.auth
  (:require [palaute.url-helper :refer [resolve-url]]
            [palaute.authentication.cas-store :as cas-store]
            [ring.util.http-response :refer [ok]]
            [ring.util.response :as resp]
            [taoensso.timbre :as log]
            [clojure.string :as s])
  (:import [fi.vm.sade.javautils.nio.cas CasLogout]))

(defn- redirect-to-logged-out-page []
  (resp/redirect (resolve-url :cas.login)))

(defn cas-login [cas-client ticket]
  (fn []
    (when ticket
      [(.validateServiceTicketWithVirkailijaUserDetailsBlocking
         cas-client
         (resolve-url :palaute.login-success)
         ticket)
       ticket])))

(defn- role-starts-with-palaute-read?
  [role]
  (s/starts-with? role "ROLE_APP_PALAUTE_PALAUTE_READ_"))

(defn- role-starts-with-palaute-create?
  [role]
  (s/starts-with? role "ROLE_APP_PALAUTE_PALAUTE_CREATE_"))

(defn parse-organization-oids
  [roles]
  (->> roles
       (filter #(or (role-starts-with-palaute-read? %)
                    (role-starts-with-palaute-create? %)))
       (map #(last (s/split % #"_")))
       (filter #(re-matches #"1\.2\.246\.562\.[0-9]+\.[0-9]+" %))
       set))

(defn parse-palaute-rights
  [roles]
  (cond-> #{}
    (role-starts-with-palaute-create? (conj :create))
    (role-starts-with-palaute-read? (conj :read))))

(defn login [login-provider
             redirect-url
             session]
  (try
    (if-let [[userdetails ticket] (login-provider)]
      (do
        (cas-store/login ticket)
        (let [username                 (.getUser userdetails)
              roles                    (.getRoles userdetails)
              organization-oids        (parse-organization-oids roles)
              rights                   (parse-palaute-rights roles)
              oph-organization         "1.2.246.562.10.00000000001"
              oph-organization-member? (contains? organization-oids oph-organization)]
          (log/info "user" username "logged in")
          (-> (resp/redirect redirect-url)
              (assoc :session
                     {:identity {:oid        (.getHenkiloOid userdetails)
                                 :username   username
                                 :ticket     ticket
                                 :rights     rights
                                 :superuser  oph-organization-member?}}))))
      (redirect-to-logged-out-page))
    (catch Exception e
      (log/error (str "Error in login ticket handling" (.getMessage e)))
      (redirect-to-logged-out-page))))

(defn logout [session]
  (log/info "username" (-> session :identity :username) "logged out")
  (cas-store/logout (-> session :identity :ticket))
  (-> (resp/redirect (resolve-url :cas.logout))
      (assoc :session {:identity nil})))

(defn cas-initiated-logout [logout-request]
  (log/info "cas-initiated logout")
  (let [cas-logout (CasLogout.)
        ticket (.parseTicketFromLogoutRequest cas-logout logout-request)]
    (log/info "logging out ticket" ticket)
    (if (.isEmpty ticket)
      (log/error "Could not parse ticket from CAS request" logout-request)
      (cas-store/logout (.get ticket)))
    (ok)))

(defn logged-in? [request]
  (let [ticket (-> request :session :identity :ticket)]
    (cas-store/logged-in? ticket)))

(defn superuser? [request]
  (-> request :session :identity :superuser))

(defn create-rights? [request]
  (-> request :session :identity :rights :create))

(defn read-rights? [request]
  (-> request :session :identity :rights :read))
