(ns goatway.channels.xmpp.filter
  (:require [clojure.core.async :refer [chan go <! >!]]
            [clojure.tools.logging :as log]
            [goatway.runtime.db :as db]))


(defn sender-not-ignored
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
            (if (and (sender-not-ignored next) (db/stanza-not-stored stanza-id))
              (do (db/store-stanza stanza-id)
                  (log/infof "Message with :stanza-id %s is not filtered" stanza-id)
                  (>! out next))
              (log/infof "Message with :stanza-id %s was filtered" stanza-id))
            (recur))))
    out))
