(ns palaute.url-helper
  (:require [palaute.config :refer [config]])
  (:import (fi.vm.sade.properties OphProperties)))

(def ^fi.vm.sade.properties.OphProperties url-properties (atom nil))

(defn- load-config
  []
  (let [{:keys [virkailija-host hakija-host url-palaute] :or
         {virkailija-host "" hakija-host "" url-palaute ""}} (:urls config)]
    (reset! url-properties
            (doto (OphProperties. (into-array String ["/palaute-oph.properties"]))
                  (.addDefault "host-virkailija" virkailija-host)
                  (.addDefault "host-hakija" hakija-host)
                  (.addDefault "url-palaute" url-palaute)))))

(defn resolve-url
  [key & params]
  (when (nil? @url-properties)
    (load-config))
  (.url @url-properties (name key) (to-array (or params []))))
