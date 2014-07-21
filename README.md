# Gezwitscher

A basic wrapper around the java [twitter4j](http://twitter4j.org/en/index.html) framework for the [Twitter API](https://dev.twitter.com/docs). Be aware, this is very early alpha and still a work in progress. Many parts of the API could be changed, so use it with caution. Currently Gezwitscher only supports [status/filter stream](https://dev.twitter.com/docs/api/1.1/post/statuses/filter) from the Streaming API. Furthermore [search](https://dev.twitter.com/docs/api/1.1/get/search/tweets) and [user timeline](https://dev.twitter.com/docs/api/1.1/get/statuses/user_timeline) provided by the REST API. In the future more API calls from twitter4j are planned be added.

Authentification is required. Refer to the [documentation](https://dev.twitter.com/docs/auth/using-oauth) for aquiring the credentials.

## Usage

To include Gezwitscher in your project, add the following to your project.clj dependencies:

```clojure
[gezwitscher "0.1.1-SNAPSHOT"]
```

### Streaming

Set the overall configuration: (I usually pack those into an atom)

```clojure
(ns example.core
  (:require [gezwitscher.core :refer [start-filter-stream]]))

(def stream-state
  (atom
   {:credentials {:consumer-key "****"
                  :consumer-secret "****"
                  :access-token "****"
                  :access-token-secret "****"}
    :handler (fn [status] (println (:text status)))
    :follow [1460703391]
    :track ["clojure" "functional" "programming"]}))
```

The keys should be set as follows:
* `:credentials` : Punch in the API keys generated by twitter. You should find them in your twitter application management. **Do not** copy your keys in any human-readable form in your code otherwise your application could be compromised. Set them for example in your envirnoment variables.
* `:handler` : Define a function which processes incoming statuses. The incoming data is a clojure map parsed by `clojure.data.json`, refer to [twitter documentation](https://dev.twitter.com/docs/platform-objects/tweets) for available keys.
* `:follow` : Define which twitter users to follow (the twitter ids are needed, not screen names).
* `:track` : Define keywords to be tracked.


Start the streaming service
```clojure
(def stop-stream 
  (start-filter-stream 
    (:follow @stream-state)
    (:track @stream-state)
    (:handler @stream-state)
    (:credentials @stream-state)))
```

The `start-filter-stream` function returns a stop-function for the twitter-stream if you want to control the streaming.


### Searching

Set the credentials configuration as above

```clojure
(ns example.core
  (:require [gezwitscher.core :refer [create-search-fn]]))

(def query-state
  (atom
   {:credentials {:consumer-key "****"
                  :consumer-secret "****"
                  :access-token "****"
                  :access-token-secret "****"}}))
```

Create the search function and search for a keyword

```clojure
(def search (create-search-fn (:credentials @query-state)))

;; search twitter for 'clojure' and print the head entry's status text
(-> (search "clojure") first :text println)
```

### User Timeline
Set the credentials configuration again as above

```clojure
(ns example.core
  (:require [gezwitscher.core :refer [create-user-timeline-fn]]))

(def timeline-state
  (atom
   {:credentials {:consumer-key "****"
                  :consumer-secret "****"
                  :access-token "****"
                  :access-token-secret "****"}}))
```

Create the timeline function and fetch a user timeline

```clojure
(def timeline (create-user-timeline-fn (:credentials @timeline-state)))

;; fetch nytimes' twitter timeline and print the head entry's status text
(-> (timeline "nytimes") first :text println)
```

### Status Update
And again set the credentials configuration

```clojure
(ns example.core
  (:require [gezwitscher.core :refer [create-update-status-fn]]))

(def status-state
  (atom
   {:credentials {:consumer-key "****"
                  :consumer-secret "****"
                  :access-token "****"
                  :access-token-secret "****"}}))
```

Create the status update-function and post a new status

```clojure
(def update-status (create-update-status-fn (:credentials @status-state)))

;; post the new status
(update-status "Try out https://topiq.es")
```


## License

Copyright © 2014 Konrad Kühne

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
