(ns gezwitscher.core-test
  (:require [clojure.test :refer :all]
            [gezwitscher.core :refer :all]))


;; example state, never reveal credentials in code, store the in env vars
(def twitter-state
  (atom
   {:credentials {:consumer-key (or (System/getenv "TWITTER_API_KEY") "")
                  :consumer-secret (or (System/getenv "TWITTER_API_SECRET") "")
                  :access-token (or (System/getenv "TWITTER_ACCESS_TOKEN") "")
                  :access-token-secret (or (System/getenv "TWITTER_ACCESS_TOKEN_SECRET") "")}
    :handler (fn [status] (println (-> status :user :screen_name) " posts "(:text status)))
    :follow [1460703391]
    :track ["clojure" "functional" "programming"]}))


(defn test-stream []
  (let [stop-stream (start-filter-stream @twitter-state)]
    (Thread/sleep 20000)
    (stop-stream)))
