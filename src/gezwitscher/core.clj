(ns gezwitscher.core
  (:require [clojure.data.json :as json])
  (:import [twitter4j StatusListener TwitterStream TwitterStreamFactory FilterQuery Query TwitterFactory Paging]
           [twitter4j.conf ConfigurationBuilder Configuration]
           [twitter4j.json DataObjectFactory]))

(set! *warn-on-reflection* true)

(defn- build-config
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


(defn- get-twitter-factory
  "Creates a twitter factory"
  [credentials]
  (let [factory (TwitterFactory. (build-config credentials))]
    (.getInstance factory)))


(defn- get-twitter-stream-factory
  "Creates a twitter stream factory"
  [credentials]
  (let [factory (TwitterStreamFactory. (build-config credentials))]
    (.getInstance factory)))


(defn start-filter-stream
  "Starts streaming, following given ids, tracking given keywords, handling incoming tweets with provided handler function"
  [follow track handler credentials]
  (let [filter-query (FilterQuery. 0 (long-array follow) (into-array String track))
        stream (get-twitter-stream-factory credentials)]
    (.addListener stream (status-listener handler))
    (.filter stream filter-query)
    (fn [] (do
            (.shutdown stream)
            (println "Streaming stopped!")))))


(defn make-searcher
  "Creates a twitter search function given credentials and amount, limited to 100 tweets. Returned function requires a keyword as parameter."
  ;;TODO workaround to obtain more tweets
  [credentials counter]
  (let [twitter (get-twitter-factory credentials)]
    (fn [search-string]
      (let [query (Query. search-string)
            result (do (.setCount query counter)
                       (.search twitter query))]
        (map #(json/read-str (DataObjectFactory/getRawJSON %) :key-fn keyword) (.getTweets result))))))


(defn make-timeliner
  "Creates a function for twitter timeline fetches, limited to 200 tweets. Returned function requires a user as parameter."
  [credentials]
  (let [twitter (get-twitter-factory credentials)
        page (Paging. (int 1) (int 300))]
    (fn [user]
      (map #(json/read-str (DataObjectFactory/getRawJSON %) :key-fn keyword) (.getUserTimeline twitter user page)))))


(defn make-status-updater
  "Creates a function that allows status updates using the current account. Returned function requires a text-string as parameter."
  [credentials]
  (let [twitter (get-twitter-factory credentials)]
    (fn [status-string]
      (.updateStatus twitter status-string))))
