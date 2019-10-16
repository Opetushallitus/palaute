(ns palaute.palaute-schema
  (:require
    [schema.core :as s]
    [ring.swagger.coerce :as coerce]
    [schema.coerce :as c])
  (:import java.util.Locale
           java.time.ZonedDateTime
           org.joda.time.DateTime
           java.util.Date
           org.joda.time.DateTimeZone
           org.joda.time.format.DateTimeFormat
           org.joda.time.format.ISODateTimeFormat
           org.joda.time.format.DateTimeFormatterBuilder
           java.time.format.DateTimeFormatter))

(defonce formatter (ISODateTimeFormat/dateTime))
(defonce zone-id (DateTimeZone/forID "Europe/Helsinki"))

(s/defschema Feedback
  {:feedback   s/Str
   :key        s/Str
   :created-at DateTime
   :stars      (s/pred #(<= 1 % 5))
   :user-agent s/Str
   :data       (s/maybe s/Any)})

(defmulti time-matcher identity)

(defn parse-date-time ^DateTime [date] (DateTime. date DateTimeZone/UTC))

(defmethod time-matcher DateTime [_] parse-date-time)
(defmethod time-matcher :default [_] nil)

(defn json-schema-coercion-matcher
  [schema]
  (or (coerce/json-coersions schema)
      (c/keyword-enum-matcher schema)
      (coerce/set-matcher schema)
      (time-matcher schema)
      (coerce/pattern-matcher schema)))

(defonce FeedbackEnforcer (c/coercer! Feedback json-schema-coercion-matcher))
