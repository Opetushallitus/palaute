(ns palaute.authentication.user-rights
  (:require [palaute.config :refer [config]]
            [schema.core :as s]))

(def ^:private
     oikeus-to-right
  {{:palvelu "PALAUTE" :oikeus "PALAUTE_READ"} :read
   {:palvelu "PALAUTE" :oikeus "PALAUTE_CREATE"} :create})

(def right-names (vals oikeus-to-right))

(s/defschema Right (apply s/enum right-names))

(defn virkailija->right-organization-oids
  [virkailija rights]
  {:pre [(< 0 (count rights))]}
  (select-keys (->> (:organisaatiot virkailija)
                    (mapcat (fn [{:keys [organisaatioOid kayttooikeudet]}]
                              (prn kayttooikeudet)
                              (map (fn [right]

                                     {right [organisaatioOid]})
                                   (keep oikeus-to-right kayttooikeudet))))
                    (reduce (partial merge-with concat) {}))
               rights))
