(ns goatway.channels.xmpp.filter
  (:require [clojure.core.async :refer [chan go <! >!]]
            [amalloy.ring-buffer :as ring-buffer])
  (:import (org.jxmpp.util XmppStringUtils)))

(def sent (atom (ring-buffer/ring-buffer 20)))

(defn matches
  "Returns true when sender not ignored and not himself"
  [{:keys [jid gw-xmpp-addr nick ignored]}]
  (not (or (get ignored nick)
           (not jid)
           (= gw-xmpp-addr (XmppStringUtils/parseBareJid jid)))))

(defn filter-chan
  "Filters messages that not ignored and was not sent before to out channel"
  [in-chan]
  (let [out (chan)]
    (go (loop []
          (let [next (<! in-chan)]
            (when
              (and (matches next) (not (some #{(:stanza-id next)} @sent)))
              (do (swap! sent into [(:stanza-id next)])
                  (>! out next)))
            (recur))))
    out))
