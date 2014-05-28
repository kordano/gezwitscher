(ns gezwitscher.core-test
  (:require [clojure.test :refer :all]
            [gezwitscher.core :refer :all]))


;; example state, never reveal credentials in code, store the in env vars
(def test-state
  {:credentials {:consumer-key (or (System/getenv "TWITTER_API_KEY") "****")
                 :consumer-secret (or (System/getenv "TWITTER_API_SECRET") "****")
                 :access-token (or (System/getenv "TWITTER_ACCESS_TOKEN") "****")
                 :access-token-secret (or (System/getenv "TWITTER_ACCESS_TOKEN_SECRET") "****")}
   :handler (fn [status] (println (:text status)))
   :follow [1460703391]
   :track ["clojure" "functional" "programming"]})

(deftest test-user-timeline-count
  (let [fetch-timeline (create-user-timeline-fn (:credentials test-state))]
    (is (= (count (fetch-timeline "FAZ_NET")) 200))))

(deftest test-search-count
  (let [search (create-search-fn (:credentials test-state))]
    (is (= (count (search "NSA")) 100))))


(comment
  (defn test-stream []
    (let [stop-stream (start-filter-stream @twitter-state)]
      (Thread/sleep 20000)
      (stop-stream)))

  (def stop-stream (start-filter-stream twitter-state))
)
