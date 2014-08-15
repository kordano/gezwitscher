(ns gezwitscher.core-test
  (:require [clojure.test :refer :all]
            [gezwitscher.core :refer :all]))


;; example state, never reveal credentials in code, store the in env vars
(def test-state
  (atom
   {:credentials {:consumer-key (or (System/getenv "TWITTER_API_KEY") "****")
                  :consumer-secret (or (System/getenv "TWITTER_API_SECRET") "****")
                  :access-token (or (System/getenv "TWITTER_ACCESS_TOKEN") "****")
                  :access-token-secret (or (System/getenv "TWITTER_ACCESS_TOKEN_SECRET") "****")}
    :handler (fn [status] (println (:text status)))
    :follow [1460703391]
    :track ["clojure" "functional" "programming"]}))


(deftest test-user-timeline-count
  (let [fetch-timeline (create-user-timeline-fn (:credentials @test-state))]
    (is (= (count (fetch-timeline "FAZ_NET")) 200))))


(deftest test-search-count
  (let [search (create-search-fn (:credentials @test-state))]
    (is (= (count (search "NSA")) 100))))




(comment

  (def creds {:consumer-key (or (System/getenv "TWITTER_API_KEY") "****")
              :consumer-secret (or (System/getenv "TWITTER_API_SECRET") "****")
              :access-token (or (System/getenv "TWITTER_ACCESS_TOKEN") "****")
              :access-token-secret (or (System/getenv "TWITTER_ACCESS_TOKEN_SECRET") "****")})

  (def track ["@FAZ_NET" "@tagesschau" "@dpa" "@SZ" "@SPIEGELONLINE" "@BILD" "@DerWesten" "@ntvde" "@tazgezwitscher" "@welt" "@ZDFheute" "@N24_de" "@sternde" "@focusonline"])

  (def follow [114508061 18016521 5734902 40227292 2834511 9204502 15071293 19232587 15243812 8720562 1101354170 15738602 18774524 5494392])


  (let [stream (start-filter-stream test-credentials :track track :follow follow)]
    (def stop-strean (:stop-fn stream))
    (go-loop [ch (:status-chan stream)]
      (let [status (<! ch)]
        (println (:text status))
        (recur ch))))

  (stop-stream)

  (def search (create-search-fn (:credentials @test-state)))

  (-> (search "clojure") first :text)

  (def timeline (create-user-timeline-fn (:credentials @test-state)))

  (-> (timeline "nytimes") first :text)

  )
