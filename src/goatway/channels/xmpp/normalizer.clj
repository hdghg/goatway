(ns goatway.channels.xmpp.normalizer
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :refer [chan go <! >!]])
  (:import (org.jivesoftware.smack.packet Message)
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

(defn clarify-merge [all stanza muc]
  (try
    (if (instance? Message stanza)
      (let [new-values (clarify stanza muc)]
        (log/infof "Adding following parameters to stanza-id %s: %s"
                   (.getStanzaId stanza) new-values)
        (merge all new-values)))
    (catch Exception ex
      (log/warnf "Cannot clarify packet %s" ex))))


(defn normalizer-chan
  "Channel that fills parameter map with info about sender, body and so on"
  [in-chan]
  (let [out (chan)]
    (go (loop []
        (let [{:keys [stanza muc] :as all} (<! in-chan)
              merged (clarify-merge all stanza muc)]
          (when merged (>! out merged)))
        (recur)))
    out))
