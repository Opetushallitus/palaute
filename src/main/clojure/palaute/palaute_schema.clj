(ns palaute.palaute-schema
  (:require
    [schema.core :as s]
    [schema.coerce :as c]))

(s/defschema Feedback
  {:feedback   s/Str
   :key        s/Str
   :stars      (s/pred #(<= 1 % 5))
   :user-agent s/Str
   :data       (s/maybe s/Any)})

(defonce FeedbackEnforcer (c/coercer! Feedback c/json-coercion-matcher))
