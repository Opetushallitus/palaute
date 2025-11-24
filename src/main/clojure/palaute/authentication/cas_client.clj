(ns palaute.authentication.cas-client
  (:require [palaute.url-helper :refer [resolve-url]]
            [palaute.config :refer [config]])
  (:import [fi.vm.sade.javautils.nio.cas CasClientBuilder CasConfig$CasConfigBuilder]))

(defrecord CasClientState [client session-cookie-name session-id])

(defn new-cas-client []
  (CasClientBuilder/build (-> (new CasConfig$CasConfigBuilder
                                (get-in config [:cas :username])
                                (get-in config [:cas :password])
                                (resolve-url :cas-client)
                                ""
                                "palaute"
                                (get-in config [:caller-id :backend])
                                "")
                              (.setJsessionName "JSESSIONID")
                              (.build))))
