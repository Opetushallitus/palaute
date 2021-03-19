(ns palaute.authentication.auth
  (:require [palaute.url-helper :refer [resolve-url]]
            [palaute.db :as db]
            [palaute.authentication.cas-store :as cas-store]
            [palaute.authentication.kayttooikeus-client :refer [get-kayttooikeudet]]
            [environ.core :refer [env]]
            [medley.core :refer [map-kv]]
            [palaute.authentication.user-rights :as rights]
            [ring.util.http-response :refer [ok]]
            [ring.util.response :as resp]
            [taoensso.timbre :as log]
            [yesql.core :as sql])
  (:import (fi.vm.sade.utils.cas CasLogout)))

(defn- redirect-to-logged-out-page []
  (resp/redirect (resolve-url :cas.login)))

(defn cas-login [cas-client ticket]
  (fn []
    (when ticket
      [(.run
         (.validateServiceTicketWithVirkailijaUsername cas-client (resolve-url :palaute.login-success) ticket))
       ticket])))

(defn login [login-provider
             redirect-url
             session]
  (try
    (if-let [[username ticket] (login-provider)]
      (do
        (cas-store/login ticket)
        (let [virkailija                (get-kayttooikeudet username)
              right-organization-oids   (rights/virkailija->right-organization-oids virkailija rights/right-names)
              organization-oids         (->> (-> virkailija :organisaatiot)
                                             (map :organisaatioOid)
                                             (set))
              oph-organization          "1.2.246.562.10.00000000001"
              oph-organization-member?  (contains? organization-oids oph-organization)]
          (log/info "user" username "logged in")
          (-> (resp/redirect redirect-url)
              (assoc :session
                     {:identity {:oid        (:oidHenkilo virkailija)
                                 :username   username
                                 :ticket     ticket
                                 :rights     right-organization-oids
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
  (let [ticket (CasLogout/parseTicketFromLogoutRequest logout-request)]
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
