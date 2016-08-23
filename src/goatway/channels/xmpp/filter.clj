(ns goatway.channels.xmpp.filter
  (:require [clojure.core.async :refer [chan go <! >!]]
            [amalloy.ring-buffer :as ring-buffer]
            [clojure.tools.logging :as log]
            [goatway.runtime.db :as db]))

(def sent (atom (ring-buffer/ring-buffer 20)))

(defn matches
  "Returns true when sender not ignored and not himself"
  [{:keys [nick ignored body]}]
  (not (or (get ignored nick)
           (get @db/puppets nick)
           (not body))))

(defn filter-chan
  "Filters messages that not ignored and was not sent before to out channel"
  [in-chan]
  (let [out (chan)]
    (go (loop []
          (let [next (<! in-chan)
                stanza-id (:stanza-id next)]
            (log/infof "I take data: :stanza-id %s" stanza-id)
            (if (and (matches next) (not (some #{stanza-id} @sent)))
              (do (swap! sent into [stanza-id])
                  (log/infof "Message with :stanza-id %s is not filtered" stanza-id)
                  (>! out next))
              (log/infof "Message with :stanza-id %s was filtered" stanza-id))
            (recur))))
    out))
