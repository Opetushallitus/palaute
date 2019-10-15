(ns palaute.sqs
  (:require [palaute.config :refer [config]]
            [yesql.core :as sql]
            [palaute.db :refer [exec]]
            [palaute.palaute-schema :refer [Feedback FeedbackEnforcer]]
            [camel-snake-kebab.core :refer [->snake_case ->kebab-case-keyword ->camelCase]]
            [camel-snake-kebab.extras :refer [transform-keys]]
            [cheshire.core :as json]
            [taoensso.timbre :as log])
  (:import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
           java.io.Closeable
           java.time.Duration
           com.amazonaws.services.sqs.AmazonSQSClientBuilder
           [com.amazonaws.services.sqs.model
            ReceiveMessageRequest
            Message
            DeleteMessageBatchRequestEntry]))

(sql/defqueries "sql/palaute.sql")

(defn save-feedback [feedback]
  (try
    (exec yesql-insert-feedback<!
          (->> feedback
               (transform-keys ->snake_case)))
    (catch Exception e
      (log/error (str "Error saving feedback: " (.getMessage e))))))

(defn unload-sqs-queue []
  (when-not (-> config :dev)
    (.start
      (Thread.
        (fn []
          (log/info "Starting to unload SQS Queue")
          (with-open [credentials (DefaultAWSCredentialsProviderChain/getInstance)]
            (let [amazon-sqs      (-> (AmazonSQSClientBuilder/standard)
                                      (.withRegion (:region (:aws config)))
                                      (.withCredentials credentials)
                                      .build)]
              (loop []
                (try
                  (let [messages (->>
                                  (-> (new ReceiveMessageRequest)
                                      (.withQueueUrl (:queue-url (:aws config)))
                                      (.withWaitTimeSeconds
                                        (.intValue (.getSeconds (Duration/ofSeconds 20)))))
                                  (.receiveMessage amazon-sqs)
                                  .getMessages
                                  seq)]
                    (doseq [message messages]
                      (if-let [body (.getBody message)]
                        (save-feedback
                         (->> (FeedbackEnforcer (json/parse-string body true))
                              (transform-keys ->snake_case)))))
                    (when (and messages (not-empty messages))
                      (log/info (str "Got something! " (type messages)))
                      (log/info (str messages))
                      (log/info (str (type (first messages))))))
                  (catch Exception e
                    (log/error (str "Error while listening SQS: " (.getMessage e)))))
                (recur)))))))))

