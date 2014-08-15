(defproject gezwitscher "0.1.1-SNAPSHOT"

  :description "Basic wrapper around the twitter4j framework"

  :url "https://github.com/kordano/gezwitscher"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/core.async "0.1.303.0-886421-alpha"]
                 [org.clojure/data.json "0.2.5"]
                 [org.twitter4j/twitter4j-core "4.0.2"]
                 [org.twitter4j/twitter4j-stream "4.0.2"]]

  :scm {:name "git"
        :tag "6e0797a142049fe07d395db7b0dbdd4da3fa34fc"
        :url "https://github.com/kordano/gezwitscher.git"})
