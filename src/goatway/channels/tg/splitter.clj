(ns goatway.channels.tg.splitter
  (:require [clojure.tools.logging :as log]
            [clojure.core.async :refer [chan go <! >!]]
            [clojure.string :as str]
            [goatway.utils.string :as u]
            [goatway.utils.xmpp :as xmpp-u])
  (:import (java.util WeakHashMap)
           (org.jivesoftware.smack.tcp XMPPTCPConnection XMPPTCPConnectionConfiguration)
           (org.jivesoftware.smackx.muc MultiUserChatManager MultiUserChat)
           (org.jivesoftware.smack AbstractConnectionListener)))

(def connections (WeakHashMap.))

(defn- get-stored [uid]
  (.get ^WeakHashMap connections uid))

(defn- store [uid connection]
  (.put ^WeakHashMap connections uid connection))

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
    (.addConnectionListener
      conn
      (proxy [AbstractConnectionListener] []
        (authenticated [_ _]
          (log/infof "smack-splitter: %s authenticated" sender)
          (swap! goatway.channels.xmpp.filter/ignored-local conj (xmpp-u/join-muc muc sender)))))
    (-> conn .connect .login)
    [conn muc]))

(defn send-as
  "Send message behalf given sender"
  [^XMPPTCPConnection conn ^MultiUserChat muc sender ^String message-text]
  (when (not (.isConnected conn)) (.connect conn))
  (when (not (.isJoined muc)) (.join muc sender))
  (.sendMessage muc message-text))

(defn split-send
  "Take message from channel and send it behalf :sender"
  [in-chan]
  (go
    (loop []
      (let [{:keys [sender message-text api-key
                    gw-xmpp-addr gw-xmpp-passwd gw-xmpp-room]} (<! in-chan)
            uid {:sender sender :api-key api-key}
            [conn muc] (or (get-stored uid)
                           (new-conn gw-xmpp-addr gw-xmpp-passwd gw-xmpp-room sender))]
        (send-as conn muc sender message-text)
        (store uid [conn muc]))
      (recur))))
