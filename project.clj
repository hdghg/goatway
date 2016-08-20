(defproject goatway "0.5.1"
  :description "Simple gateway between jabber muc and telegram chat"
  :url "http://github.com/hdghg/goatway"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.clojure/core.async "0.2.385"]
                 [hdghg/gram-api "0.4.1"]
                 [org.igniterealtime.smack/smack-tcp "4.1.8"]
                 [org.igniterealtime.smack/smack-java7 "4.1.8"]
                 [org.igniterealtime.smack/smack-im "4.1.8"]
                 [org.igniterealtime.smack/smack-extensions "4.1.8"]
                 [environ "1.1.0"]
                 [ch.qos.logback/logback-classic "1.1.7"]
                 [org.slf4j/jul-to-slf4j "1.7.20"]
                 [amalloy/ring-buffer "1.2.1"]]
  :main goatway.standalone
  :profiles {:uberjar {:aot :all}}
  :global-vars {*warn-on-reflection* true})
