(ns gezwitscher.core
  (:require [clojure.data.json :as json])
  (:import [twitter4j StatusListener TwitterStream TwitterStreamFactory FilterQuery]
           [twitter4j.conf ConfigurationBuilder Configuration]
           [twitter4j.json DataObjectFactory]))

(set! *warn-on-reflection* true)


(defn build-config
  "Twitter stream configuration"
  ^Configuration [{:keys [consumer-key consumer-secret access-token access-token-secret]}]
  (let [cb (ConfigurationBuilder.)]
    (.setDebugEnabled cb true)
    (.setOAuthConsumerKey cb consumer-key)
    (.setOAuthConsumerSecret cb consumer-secret)
    (.setOAuthAccessToken cb access-token)
    (.setOAuthAccessTokenSecret cb access-token-secret)
    (.setJSONStoreEnabled cb true)
    (.build cb)))


(defn- status-listener
  "Stream handler, applies given function to newly retrieved status"
  [func]
  (proxy [StatusListener] []
    (onStatus [^twitter4j.Status status]
      (let [parsed-status (json/read-str (DataObjectFactory/getRawJSON status) :key-fn keyword)]
        (func parsed-status)))
    (onException [^java.lang.Exception e] (.printStackTrace e))
    (onDeletionNotice [^twitter4j.StatusDeletionNotice statusDeletionNotice] ())
    (onScrubGeo [userId upToStatusId] ())
    (onTrackLimitationNotice [numberOfLimitedStatuses] ())))


(defn- get-twitter-stream-factory
  "Creates the twitter factory for the stream object"
  [state]
  (let [factory (TwitterStreamFactory. (build-config (:credentials state)))]
    (.getInstance factory)))


(defn start-filter-stream
  "Starts streaming, following given ids and tracking given keywords"
  [state]
  (let [filter-query (FilterQuery. 0 (long-array (:follow state)) (into-array String (:track state)))
        stream (get-twitter-stream-factory state)]
    (.addListener stream (status-listener (:handler state)))
    (.filter stream filter-query)
    (fn [] (do
            (.shutdown stream)
            (println "Streaming stopped!")))))
