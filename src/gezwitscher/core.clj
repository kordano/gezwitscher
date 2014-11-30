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


(defn- new-status-listener
  "Stream handler, applies given function to newly retrieved status"
  [status-ch error-ch]
  (proxy [StatusListener] []
    (onStatus [^twitter4j.Status status]
      (let [parsed-status (json/read-str (DataObjectFactory/getRawJSON status) :key-fn keyword)]
        (put! status-ch parsed-status)))
    (onException [^java.lang.Exception e] (put! error-ch {:error e :data (.getStackTrace e)}))
    (onDeletionNotice [^twitter4j.StatusDeletionNotice deletion-notice] (put! status-ch deletion-notice))
    (onScrubGeo [userId upToStatusId] ())
    (onTrackLimitationNotice [numberOfLimitedStatuses number-of-limited-statuses] (put! status-ch number-of-limited-statuses ))))


(defn- status-listener
  "Stream handler, applies given function to newly retrieved status"
  [func error-func]
  (proxy [StatusListener] []
    (onStatus [^twitter4j.Status status]
      (let [parsed-status (json/read-str (DataObjectFactory/getRawJSON status) :key-fn keyword)]
        (func parsed-status)))
    (onException [^java.lang.Exception e] (error-func e))
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
  [follow track handler credentials error-handler]
  (let [filter-query (FilterQuery. 0 (long-array follow) (into-array String track))
        stream (get-twitter-stream-factory credentials)]
    (.addListener stream (status-listener handler error-handler))
    (.filter stream filter-query)
    (fn [] (.shutdown stream))))


(defn make-filter-streamer
  "Similar to start-filter-stream but uses channels to communicate"
  [stream & params]
  (let [options (apply hash-map params)
        filter-query (FilterQuery. 0 (long-array (:follow options)) (into-array String (:track options)))
        status-chan (chan)
        error-chan (chan)]
    (.addListener stream (new-status-listener status-chan error-chan))
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


(defn- timelined
  "Answers to a timeline request"
  [twitter timeline-ch out]
  (let [timeline (make-timeliner twitter)]
    (go-loop [{:keys [user] :as t} (<! timeline-ch)]
      (when t
        (>! out {:result (vec (timeline user)) :topic (:topic t)})
        (recur (<! timeline-ch))))))


(defn- status-updated
  "Answers to a update-status request"
  [twitter status-update-ch out]
  (let [update-status (make-status-updater twitter)]
    (go-loop [{:keys [text] :as s} (<! status-update-ch)]
      (when s
        (>! out {:status (update-status text) :topic (:topic s)})
        (recur (<! status-update-ch))))))


(defn- searched
  "Answers to a search request"
  [twitter search-ch out]
  (let [search (make-searcher twitter)]
    (go-loop [{:keys [text] :as s} (<! search-ch)]
      (when s
        (>! out {:result (vec (search text)) :topic (:topic s)})
        (recur (<! search-ch))))))


(defn- stream-started
  "Answers to a start-stream and stop-stream request"
  [stream stream-ch out]
  (let [stop-stream (fn [] (.shutdown stream))]
    (go-loop [{:keys [track follow topic] :as s} (<! stream-ch)]
      (when s
        (case topic
          :stop-stream (do
                         (.shutdown stream)
                         (>! out :stopped))
          :start-stream (>! out {:status-ch (make-filter-streamer stream :track track :follow follow)
                                 :topic (:topic s)})
          :unrelated)
        (recur (<! stream-ch))))))


(defn- in-dispatch
  "Dispatches incoming requests"
  [{:keys [topic]}]
  (case topic
    :stop-stream :stream
    :start-stream :stream
    :search :search
    :timeline :timeline
    :update-status :update-status
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

    (sub p :update-status status-update-ch)
    (status-updated twitter status-update-ch out)

    (sub p :unrelated out)

    [in out]))
