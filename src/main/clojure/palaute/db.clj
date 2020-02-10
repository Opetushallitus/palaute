(ns palaute.db
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as string]
            [palaute.extensions]
            [palaute.palaute-schema :refer [Feedback FeedbackEnforcer]]
            [camel-snake-kebab.core :refer [->snake_case ->kebab-case-keyword ->camelCase]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [hikari-cp.core :refer :all]))

(defn- datasource-spec
  [ds]
  (merge {:auto-commit        false
          :read-only          false
          :connection-timeout 30000
          :validation-timeout 5000
          :idle-timeout       600000
          :max-lifetime       1800000
          :minimum-idle       10
          :maximum-pool-size  10
          :pool-name          "db-pool"
          :adapter            "postgresql"}
         ds))

(sql/defqueries "sql/palaute.sql")

(defonce datasource (atom nil))

(defn set-datasource [ds]
  (reset! datasource (make-datasource (datasource-spec ds))))

(defn get-datasource []
  @datasource)

(defmacro exec [query params]
  `(jdbc/with-db-transaction [connection# {:datasource (get-datasource)}]
    (~query ~params {:connection connection#})))

(defmacro exec-all [query-list]
  `(jdbc/with-db-transaction [connection# {:datasource (get-datasource)}]
    (last (for [[query# params#] (partition 2 ~query-list)]
            (query# params# {:connection connection#})))))

(defn store-feedback [feedback]
  (exec yesql-insert-feedback<!
    (->> feedback
         (transform-keys ->snake_case)
         #(update % :service (fn [service] (or service "ataru"))))))
