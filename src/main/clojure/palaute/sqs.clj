(ns palaute.sqs
  (:require [palaute.config :refer [config]]
            [yesql.core :as sql]
            [palaute.db :refer [store-feedback]]
            [palaute.palaute-schema :refer [Feedback FeedbackEnforcer]]
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

(defn save-message [message]
  (let [msg (atom nil)]
  (try
    (reset! msg (str (.getBody message)))
    (let [feedback (FeedbackEnforcer (json/parse-string @msg true))]
      (store-feedback feedback))
    (catch Exception e
      (log/error (str "Error saving feedback: " (.getMessage e) ". Message: " @msg))))))

(defn batch-receive [amazon-sqs]
  (->>
   (-> (new ReceiveMessageRequest)
       (.withQueueUrl (:queue-url (:aws config)))
       (.withWaitTimeSeconds
         (.intValue (.getSeconds (Duration/ofSeconds 20)))))
   (.receiveMessage amazon-sqs)
   .getMessages
   seq))

(defn batch-delete [amazon-sqs messages]
  (when (seq messages)
    (when-let [failed (->> messages
                           (map-indexed
                            (fn [i message]
                              (new DeleteMessageBatchRequestEntry
                                (str i)
                                (.getReceiptHandle message))))
                           (.deleteMessageBatch amazon-sqs (:queue-url (:aws config)))
                           .getFailed
                           seq)]
      (throw
        (new RuntimeException
          (->> failed
               (map #(.getMessages %))
               (clojure.string/join "; ")))))))

(defn unload-sqs-queue []
  (when-not (-> config :dev)
    (.start
      (Thread.
        (fn []
            (log/info "Starting to unload SQS Queue")
            (let [amazon-sqs (-> (AmazonSQSClientBuilder/standard)
                                 (.withRegion (:region (:aws config)))
                                 (.withCredentials (DefaultAWSCredentialsProviderChain/getInstance))
                                 .build)]
              (loop []
                (try
                  (let [messages (batch-receive amazon-sqs)]
                    (doseq [message messages]
                      (save-message message))
                    (batch-delete amazon-sqs messages))
                  (catch Exception e
                    (log/error (str "Error while listening SQS: " (.getMessage e)))))
                (recur))))))))

