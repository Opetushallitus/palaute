(ns palaute.index
  (:require [hiccup.core :refer :all]))

(def index
  (html
   [:html
    {:lang "fi"}
    [:head
     [:meta {:charset "UTF-8"}]
     [:link {:rel "shortcut icon" :href "/palaute/favicon.ico"}]
     [:title "Palaute"]]
    [:body
     [:div#app]
     [:script {:src "/palaute/index.js"}]]]))
