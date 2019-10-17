(ns palaute.authentication.kayttooikeus-client
  (:require [palaute.authentication.cas-client :as cas]
            [palaute.url-helper :as url]
            [palaute.config :refer [config]]
            [cheshire.core :as json]))

(defonce kayttooikeus-cas-client
  (cas/new-client "/kayttooikeus-service" "j_spring_cas_security_check" "JSESSIONID"))

(defn get-kayttooikeudet [username]
  (if (-> config :dev)
    {:oidHenkilo    "1.2.246.562.11.11111111000"
     :organisaatiot [{:organisaatioOid "1.2.246.562.10.1"
                      :kayttooikeudet  [{:palvelu "PALAUTE"
                                         :oikeus  "PALAUTE_READ"}
                                        {:palvelu "PALAUTE"
                                         :oikeus  "PALAUTE_CREATE"}]}
                     {:organisaatioOid "1.2.246.562.28.2"
                      :kayttooikeudet  [{:palvelu "PALAUTE"
                                         :oikeus  "PALAUTE_CREATE"}]}]}
    (let [url                   (url/resolve-url :kayttooikeus-service.kayttooikeus.kayttaja
                                                 {"username" username})
          {:keys [status body]} (cas/cas-authenticated-get kayttooikeus-cas-client url)]
      (if (= 200 status)
        (if-let [virkailija (first (json/parse-string body true))]
          virkailija
          (throw
            (new RuntimeException
              (str "No virkailija found by username " username))))
        (throw
          (new RuntimeException
            (str "Could not get virkailija by username " username
                 ", status: " status
                 ", body: " body)))))))
