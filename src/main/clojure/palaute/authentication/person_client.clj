(ns palaute.authentication.person-client)

(defn get-person [oid]
  {:oidHenkilo   "1.2.3.4.5.6"
   :hetu         "020202A0202"
   :etunimet     "Testi"
   :kutsumanimi  "Testi"
   :sukunimi     "Ihminen"
   :syntymaaika  "1941-06-16"
   :sukupuoli    "2"
   :kansalaisuus [{:kansalaisuusKoodi "246"}]
   :aidinkieli   {:id          "742310"
                  :kieliKoodi  "fi"
                  :kieliTyyppi "suomi"}
   :turvakielto  false
   :yksiloity    false
   :yksiloityVTJ false})
