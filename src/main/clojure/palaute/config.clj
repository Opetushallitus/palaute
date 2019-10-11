(ns palaute.config
  (:require [clojure.edn :as edn]
            [environ.core :refer [env]]))

(defonce config (-> (get env :palaute-config "config/config.edn")
                    (slurp)
                    (edn/read-string)))

