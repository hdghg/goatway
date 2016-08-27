(ns goatway.channels.tg.splitter
  (:require [clojure.core.async :refer [chan go <! >!]]
            [clojure.string :as str]
            [goatway.utils.string :as u]
            [goatway.utils.xmpp :as xmpp-u]
            [goatway.channels.xmpp.filter :as xmpp-filter])
  (:import (org.jivesoftware.smack.tcp XMPPTCPConnection XMPPTCPConnectionConfiguration)
           (org.jivesoftware.smackx.muc MultiUserChatManager MultiUserChat)
           (org.jivesoftware.smack.packet Message)))

(def connections (atom {}))

(defn new-conn
  "Create new xmpp connection for given participant using given credentials"
  [xmpp-addr xmpp-passwd xmpp-room sender]
  (let [[login server] (str/split xmpp-addr #"@" 2)
        config (-> (XMPPTCPConnectionConfiguration/builder)
                   (.setUsernameAndPassword login xmpp-passwd)
                   (.setServiceName server)
                   (.setResource (u/random-string 16))
                   (.build))
        conn (XMPPTCPConnection. config)
        mucm (MultiUserChatManager/getInstanceFor conn)
        muc (.getMultiUserChat mucm xmpp-room)]
    (.addConnectionListener conn (xmpp-u/create-listener muc sender))
    (-> conn .connect .login)
    [conn muc]))

(defn send-as
  "Send message behalf given sender"
  [^XMPPTCPConnection conn ^MultiUserChat muc sender ^String message-text]
  (when (not (.isConnected conn)) (.connect conn))
  (when (not (.isJoined muc)) (.join muc sender))
  (let [stanza-id (u/random-string 8)
        msg (doto (Message.) (.setBody message-text) (.setStanzaId stanza-id))]
    (swap! xmpp-filter/my-own into [stanza-id])
    (.sendMessage muc msg)))

(defn split-send
  "Take message from channel and send it behalf :sender"
  [in-chan]
  (go
    (loop []
      (let [{:keys [sender message-text api-key
                    gw-xmpp-addr gw-xmpp-passwd gw-xmpp-room]} (<! in-chan)
            uid {:sender sender :api-key api-key}
            [conn muc] (or (@connections uid)
                           (new-conn gw-xmpp-addr gw-xmpp-passwd gw-xmpp-room sender))]
        (send-as conn muc sender message-text)
        (swap! connections assoc uid [conn muc]))
      (recur))))
