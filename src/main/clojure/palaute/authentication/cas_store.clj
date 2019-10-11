(ns palaute.authentication.cas-store
  (:require [palaute.db :refer [exec]]
            [yesql.core :refer [defqueries]]))

(defqueries "sql/cas-ticketstore-queries.sql")

(defn login [ticket]
  (exec yesql-add-ticket-query! {:ticket ticket}))

(defn logout [ticket]
  (exec yesql-remove-ticket-query! {:ticket ticket}))

(defn logged-in? [ticket]
  (first (exec yesql-ticket-exists-query {:ticket ticket})))
