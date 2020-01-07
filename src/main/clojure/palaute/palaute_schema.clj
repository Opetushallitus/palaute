(ns palaute.palaute-schema
  (:require
    [schema.core :as s]
    [ring.swagger.coerce :as coerce]
    [schema.coerce :as c]
    [palaute.schema-util :refer [describe]])
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
  (describe
    "Palaute"
    :feedback (s/maybe s/Str) "Palautteen sisältö"
    :key s/Str "Avain joka yksilöi mitä palaute koskee"
    :created-at DateTime "Aikaleima jolloin palaute on onnettu"
    :stars (s/both s/Int (s/pred #(<= 1 % 5))) "Arvostelu tähtien määrällä (1-5 tähteä)"
    :user-agent s/Str "Palautteen antajan selaimen käyttäjäagentti"
    :data (s/maybe s/Any) "Muuta palautteeseen liittyvää järjestelmäkohtaista metadataa"))

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
