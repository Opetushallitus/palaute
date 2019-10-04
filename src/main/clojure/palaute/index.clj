(ns palaute.index
  (:require [hiccup.core :refer :all]))

(def index
  (html
   [:html
    {:lang "fi"}
    [:head
     [:meta {:charset "UTF-8"}]
     [:title "Palaute"]]
    [:body
     [:div#app]
     [:script {:src "index.js"}]]]))
