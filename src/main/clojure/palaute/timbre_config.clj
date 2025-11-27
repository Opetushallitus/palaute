(ns palaute.timbre-config
  (:require [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.community.rolling :refer [rolling-appender]]
            [environ.core :refer [env]]
            [palaute.config :refer [config]])
  (:import [java.util TimeZone]))

(defn configure-logging! []
  (timbre/merge-config!
   {:level          :info
    :appenders
    {:file-appender
     (rolling-appender
      {:path    (str (-> config :log :base-path)
                     "/app_palaute"
                     (when (:hostname env) (str "_" (:hostname env))))
       :pattern :daily})}
    :timestamp-opts {:pattern  "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
                     :timezone (TimeZone/getTimeZone "Europe/Helsinki")}
    :output-fn      (partial timbre/default-output-fn {:stacktrace-fonts {}})}))
