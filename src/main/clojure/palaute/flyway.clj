(ns palaute.flyway
  (:gen-class)
  (:require [palaute.db :as db])
  (:import [org.flywaydb.core Flyway]
           [org.flywaydb.core.api MigrationVersion]))


(defn migrate [{:keys [currentSchema] :as ds}]
  (let [flyway      (doto (Flyway.)
                          (.setSchemas (into-array String [currentSchema]))
                          (.setDataSource (db/get-datasource)))]
    (try (.migrate flyway)
      (catch Throwable e
        (prn e)
        (throw e)))))

(defmacro defmigration [name version description & body]
  `(deftype ~name []
    JdbcMigration
    (migrate [~'this ~'connection]
             ~@body)

    MigrationInfoProvider
    (getDescription [~'this] ~description)
    (getVersion [~'this] (MigrationVersion/fromVersion ~version))))
