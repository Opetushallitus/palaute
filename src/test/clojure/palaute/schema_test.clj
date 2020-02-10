(ns palaute.schema-test
  (:require [clojure.test :refer :all]
            [clojure.java.jdbc :as j]
            [palaute.palaute-schema :refer [Feedback FeedbackEnforcer]]))

(deftest schema-coercion-test
  (let [raw      {:feedback   "feedback"
                  :key        "url"
                  :user-agent "browser"
                  :data       {}
                  :created-at (System/currentTimeMillis)
                  :stars      4}
        feedback (is (FeedbackEnforcer raw))]
    feedback))
