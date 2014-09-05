(ns gezwitscher.core
  (:require [clojure.data.json :as json]
            [clojure.core.async :refer [pub sub chan put! >! <! go go-loop timeout]])
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
  [status-ch]
  (proxy [StatusListener] []
    (onStatus [^twitter4j.Status status]
      (let [parsed-status (json/read-str (DataObjectFactory/getRawJSON status) :key-fn keyword)]
        (put! status-ch parsed-status)))
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


(defn make-filter-streamer
  "Similar to start-filter-stream but uses channels to communicate"
  [stream & params]
  (let [options (apply hash-map params)
        filter-query (FilterQuery. 0 (long-array (:follow options)) (into-array String (:track options)))
        status-chan (chan)]
    (.addListener stream (status-listener status-chan))
    (.filter stream filter-query)
    status-chan))


(defn- make-searcher
  "Creates a twitter search function given credentials and amount, limited to 100 tweets. Returned function requires a keyword as parameter."
  ;;TODO workaround to obtain more tweets
  [twitter]
  (fn [search-string]
    (let [query (Query. search-string)
          result (do (.setCount query 100)
                     (.search twitter query))]
      (map #(json/read-str (DataObjectFactory/getRawJSON %) :key-fn keyword) (.getTweets result)))))


(defn- make-timeliner
  "Creates a function for twitter timeline fetches, limited to 200 tweets. Returned function requires a user as parameter."
  [twitter]
  (let [page (Paging. (int 1) (int 300))]
    (fn [user]
      (map #(json/read-str (DataObjectFactory/getRawJSON %) :key-fn keyword) (.getUserTimeline twitter user page)))))


(defn- make-status-updater
  "Creates a function that allows status updates using the current account. Returned function requires a text-string as parameter."
  [twitter]
  (fn [status-string]
 (json/read-str (DataObjectFactory/getRawJSON (.updateStatus twitter status-string)) :key-fn keyword)))


(defn- timelined [twitter timeline-ch out]
  (let [timeline (make-timeliner twitter)]
    (go-loop [{:keys [user] :as t} (<! timeline-ch)]
      (when t
        (>! out (assoc t :result (vec (timeline user))))
        (recur (<! timeline-ch))))))


(defn- status-updated [twitter status-update-ch out]
  (let [update-status (make-status-updater twitter)]
    (go-loop [{:keys [text] :as s} (<! status-update-ch)]
      (when s
        (>! out (assoc s :status (update-status text)))
        (recur (<! status-update-ch))))))


(defn- searched [twitter search-ch out]
  (let [search (make-searcher twitter)]
    (go-loop [{:keys [text] :as s} (<! search-ch)]
      (when s
        (>! out (assoc s :result (vec (search text))))
        (recur (<! search-ch))))))


(defn- stream-started [stream stream-ch out]
  (let [stop-stream (fn [] (.shutdown stream))]
    (go-loop [{:keys [track follow topic] :as s} (<! stream-ch)]
      (when s
        (println s)
        (case topic
          :stop-stream (do
                         (.shutdown stream)
                         (>! out {:stream-stopped true}))
          :start-stream (>! out (assoc s :status-ch (make-filter-streamer stream :track track :follow follow)))
          :unrelated)
        (recur (<! stream-ch))))))


(defn- in-dispatch [{:keys [topic]}]
  (println "IN" topic)
  (case topic
    :stop-stream :stream
    :start-stream :stream
    :search :search
    :timeline :timeline
    :status-update :status-update
    :unrelated))


(defn gezwitscher
  "Initializes the application and dispatches incoming requests"
  [credentials]
  (let [in (chan)
        out (chan)
        stream-ch (chan)
        search-ch (chan)
        timeline-ch (chan)
        status-update-ch (chan)
        p (pub in in-dispatch)
        twitter (get-twitter-factory credentials)
        stream (get-twitter-stream-factory credentials)]
    (sub p :stream stream-ch)
    (stream-started stream stream-ch out)

    (sub p :search search-ch)
    (searched twitter search-ch out)

    (sub p :timeline timeline-ch)
    (timelined twitter timeline-ch out)

    (sub p :status-update status-update-ch)
    (status-updated twitter status-update-ch out)

    (sub p :unrelated out)

    [in out]))
