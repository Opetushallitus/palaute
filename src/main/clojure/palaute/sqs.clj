(ns palaute.sqs
  (:require [palaute.config :refer [config]]
            [yesql.core :as sql]
            [palaute.db :refer [exec]]
            [taoensso.timbre :as log])
  (:import com.amazonaws.auth.DefaultAWSCredentialsProviderChain
           java.io.Closeable
           java.time.Duration
           com.amazonaws.services.sqs.AmazonSQSClientBuilder
           [com.amazonaws.services.sqs.model
            ReceiveMessageRequest
            DeleteMessageBatchRequestEntry]))

(sql/defqueries "sql/palaute.sql")

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
                    (log/info (str "Got something! " (type messages)))
                    (log/info (str messages)))
                  (catch Exception e
                    (log/error (str "Error while listening SQS: " (.getMessage e)))))
                (recur)))))))))

