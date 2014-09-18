(ns gezwitscher.core-test
  (:use midje.sweet)
  (:require [clojure.test :refer :all]
            [gezwitscher.core :refer :all]
            [clojure.core.async :refer [chan put! <!! >!! go go-loop]]))

(defn random-word [max-length]
  (->> (repeatedly #(rand-nth "0123456789abcdefghijklmnopqrstuvwxyzäöüABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÜ"))
      (take (rand-int max-length))
      (apply str)))


(facts
 (let [creds (-> "resources/credentials.edn" slurp read-string)
       [in out] (gezwitscher creds)
       track ["clojure" "prolog"]
       follow [146070339]
       word (random-word 100)]
   (>!! in {:topic :timeline :user (first follow)})
   (:topic (<!! out)) => :timeline
   (>!! in {:topic :search :text "clojure"})
   (:topic (<!! out)) => :search
   (>!! in {:topic :update-status :text word})
   (-> out <!! :status :text) => word))
