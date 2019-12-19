(ns palaute.schema-util
  (:require [ring.swagger.json-schema :as rsjs]
            [schema-tools.core :as st]))

(defn describe
  "Describe schema and its keys. Adds description to all keys. Requires
   Key Value Description triples."
  [description & kvds]
  (assert (or (seq kvds) (zero? (mod (count kvds) 3)))
          (format "%s: Invalid key-value-descriotion triples: %s"
                  description kvds))
  (rsjs/field
    (reduce
      (fn [c [k v d]]
        (assoc c k (rsjs/describe v d)))
      {}
      (partition 3 kvds))
    {:description description}))