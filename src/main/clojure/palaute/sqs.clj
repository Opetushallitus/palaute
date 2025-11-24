(ns palaute.sqs
  (:require [palaute.config :refer [config]]
            [yesql.core :as sql]
            [palaute.db :refer [store-feedback]]
            [palaute.palaute-schema :refer [Feedback FeedbackEnforcer]]
            [cheshire.core :as json]
            [taoensso.timbre :as log])
  (:import [java.time Duration]
           [software.amazon.awssdk.auth.credentials DefaultCredentialsProvider]
           [software.amazon.awssdk.regions Region]
           [software.amazon.awssdk.services.sqs SqsClient]
           [software.amazon.awssdk.services.sqs.model Message ReceiveMessageRequest DeleteMessageBatchRequestEntry DeleteMessageBatchRequest BatchResultErrorEntry]))

(defn save-message [^Message message]
  (let [msg (atom nil)]
  (try
    (reset! msg (str (.body message)))
    (let [feedback (FeedbackEnforcer (json/parse-string @msg true))]
      (store-feedback feedback))
    (catch Exception e
      (log/error (str "Error saving feedback: " (.getMessage e) ". Message: " @msg))))))

(defn batch-receive [^SqsClient amazon-sqs]
  (->> (-> (ReceiveMessageRequest/builder)
           (.queueUrl (:queue-url (:aws config)))
           (.waitTimeSeconds (.intValue (.getSeconds (Duration/ofSeconds 20))))
           (.build))
       (.receiveMessage amazon-sqs)
       (.messages)
       (seq)))

(defn batch-delete [^SqsClient amazon-sqs messages]
  (when (seq messages)
    (let [request (-> (DeleteMessageBatchRequest/builder)
                      (.entries (->> messages
                                     (map-indexed
                                       (fn [i ^Message message]
                                         (-> (DeleteMessageBatchRequestEntry/builder)
                                             (.id (str i))
                                             (.receiptHandle (.receiptHandle message))
                                             (.build))))
                                     (vec)))
                      (.queueUrl (:queue-url (:aws config)))
                      (.build))]
      (when-let [failed (-> amazon-sqs
                            (.deleteMessageBatch request)
                            (.failed)
                            (seq))]
        (throw
          (new RuntimeException
            (->> failed
                 (map #(.message ^BatchResultErrorEntry %))
                 (clojure.string/join "; "))))))))

(defn unload-sqs-queue []
  (when-not (-> config :dev)
    (.start
      (Thread.
        (fn []
            (log/info "Starting to unload SQS Queue")
            (let [amazon-sqs (-> (SqsClient/builder)
                                 (.region (Region/of (:region (:aws config))))
                                 (.credentialsProvider (DefaultCredentialsProvider/create))
                                 (.build))]
              (loop []
                (try
                  (let [messages (batch-receive amazon-sqs)]
                    (doseq [^Message message messages]
                      (save-message message))
                    (batch-delete amazon-sqs messages))
                  (catch Exception e
                    (log/error (str "Error while listening SQS: " (.getMessage e)))))
                (recur))))))))

