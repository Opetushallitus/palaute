(ns palaute.authentication.session-store
  (:require [ring.middleware.session.store :refer [SessionStore]]
            [yesql.core :refer [defqueries]]
            [palaute.db :refer [exec]])
  (:import (java.util UUID)))

(defqueries "sql/session-queries.sql")

(defn read-data [key]
  (when-let [data (:data (first (exec yesql-get-session-query {:key key})))]
    (assoc data :key key)))

(defn add-data [key data]
  (exec yesql-add-session-query! {:key key :data (dissoc data :key)})
  key)

(defn save-data [key data]
  (exec yesql-update-session-query! {:key key :data (dissoc data :key)})
  key)

(defn delete-data [key]
  (exec yesql-delete-session-query! {:key key})
  key)

(defn generate-new-random-key [] (str (UUID/randomUUID)))

(deftype DatabaseStore []
    SessionStore
  (read-session [a key]
    (read-data key))
  (write-session [_ key data]
    (if key
      (save-data key data)
      (add-data (generate-new-random-key) data)))
  (delete-session [_ key]
    (delete-data key)
    nil))

(defn create-store [] (DatabaseStore.))
