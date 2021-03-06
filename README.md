# Gezwitscher

A basic wrapper around the java [twitter4j](http://twitter4j.org/en/index.html) framework for the [Twitter API](https://dev.twitter.com/docs). Be aware, this is very early alpha and still a work in progress. Many parts of the API could be changed, so use it with caution. Currently Gezwitscher only supports [status/filter stream](https://dev.twitter.com/streaming/reference/post/statuses/filter) from the Streaming API. Furthermore [search](https://dev.twitter.com/rest/reference/get/search/tweets), [user timeline](https://dev.twitter.com/rest/reference/get/statuses/user_timeline) and [update status](https://dev.twitter.com/rest/reference/post/statuses/update) provided by the REST and POST API.

Authentification is required. Refer to the [documentation](https://dev.twitter.com/docs/auth/using-oauth) for aquiring the credentials.

As a simple collector using articles published on Twitter [ceres](https://github.com/kordano/ceres) is using this library.

## Usage

To include Gezwitscher in your project, add the following to your project.clj dependencies:

```clojure
[gezwitscher "0.1.2-SNAPSHOT"]
```

Punch in the API keys generated by twitter. You should find them in your twitter application management. **Tip**: Do not copy your keys in any form in your code otherwise your application could be compromised if you build an open ource application. Set them for example in your environment variables or read them from a local configuration file.

```clojure
(ns example.core
  (:require [gezwitscher.core :refer [gezwitscher]]))

;; the credentials should be specified as follows
(def creds
  {:consumer-key "****" 
   :consumer-secret "****"
   :access-token "****"
   :access-token-secret "****"})

;; communication is held via input and output channels 
;; where input is the connection to twitter and output is the answer
(def z (gezwitscher creds))

;; let's do something with that, shall we?
(go
  (let [[in out] z]
    ;; search something
    (>! in {:topic :search :text "clojure"})
    (println "OUT" (<! out))
    
    ;; update status of current user,
    ;; be sure that the twitter app has read and write rights
    (>! in {:topic :update-status :text "I like cake!"})
    (println "OUT" (<! out))
    
    ;; fetch the timeline of a user
    (>! in {:topic :timeline :user 16032925})
    (println "OUT" (<! out))
    
    ;; start a filtered stream 
    ;; tracking certain keywords and following some users
    (>! in {:topic :start-stream :track ["clojure" "haskell"] :follow [16032925]})
    (let [output (<! out)]
      (println "OUT" output)
      
      ;; the start-stream request returns another channel 
      ;; from where new tweets could be retrieved
      (go-loop [status (<! (:status-ch output))]
        (when status
          (println (:text status))
          (recur (<! (:status-ch output))))))
          
    ;; wait a minute
    (<! (timeout 60000))
    
    ;; stop it, just stop it!
    (>! in {:topic :stop-stream})
    (println (<! out))))
```

## License

Copyright © 2014-2015 Konrad Kühne

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
