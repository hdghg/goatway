(ns goatway.channels.xmpp.normalizer
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :refer [chan go <! >!]])
  (:import (org.jivesoftware.smack.packet Stanza Message)
           (org.jivesoftware.smackx.muc MultiUserChat Occupant)
           (org.jxmpp.util XmppStringUtils)))

(defn clarify
  "Extract sender, nick, body jid of message sender"
  [^Message message ^MultiUserChat muc]
  (let [^String from (.getFrom ^Message message)
        ^String stanza-id (.getStanzaId ^Message message)
        body (.getBody ^Message message)
        occupant (.getOccupant ^MultiUserChat muc from)
        jid (when occupant (.getJid ^Occupant occupant))
        nick (XmppStringUtils/parseResource from)]
    {:from from :stanza-id stanza-id :body body :jid jid :nick nick}))


(defn normalizer-chan
  "Channel that fills parameter map with info about sender, body and so on"
  [in-chan]
  (let [out (chan)]
    (go
      (loop []
        (let [{:keys [^Stanza stanza ^MultiUserChat muc] :as all} (<! in-chan)]
          (try
            (if (instance? Message stanza)
              (let [new-values (clarify stanza muc)
                    merged (merge all new-values)]
                (>! out merged)))
            (catch Exception ex
              (log/warn ex))))
        (recur)))
    out))
