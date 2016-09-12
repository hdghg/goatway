(ns goatway.channels.tg.splitter
  (:require [clojure.core.async :refer [chan go <! >!]]
            [clojure.string :as str]
            [goatway.utils.string :as u]
            [goatway.utils.xmpp :as xmpp-u]
            [goatway.runtime.db :as db]
            [clojure.tools.logging :as log])
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
    (log/infof "Created connection %s and muc %s" conn muc)
    [conn muc]))

(defn read-prop [settings-map key]
  (:value (first (filter #(= (:key %) key) settings-map))))

(defn read-private-xmpp-data [id]
  (let [user-settings (db/list-tg-settings id)
        private-xmpp-addr (read-prop user-settings "private-xmpp-addr")
        private-xmpp-passwd (read-prop user-settings "private-xmpp-passwd")]
    (if (and private-xmpp-addr private-xmpp-passwd)
      [private-xmpp-addr private-xmpp-passwd])))


(defn new-private-conn
  [xmpp-addr xmpp-passwd xmpp-room sender]
  (if-let [[private-xmpp-addr private-xmpp-passwd] (read-private-xmpp-data (:id sender))]
    (do (log/info "User %s uses private xmpp connectin settings")
        (new-conn private-xmpp-addr private-xmpp-passwd xmpp-room sender))
    (new-conn xmpp-addr xmpp-passwd xmpp-room sender)))

(defn send-as
  "Send message behalf given sender"
  [^XMPPTCPConnection conn ^MultiUserChat muc sender ^String message-text]
  (when (not (.isConnected conn)) (log/infof "Reconnecting %s" sender) (.connect conn))
  (when (not (.isJoined muc)) (log/infof "Rejoining as %s" sender) (xmpp-u/join-muc muc sender))
  (let [stanza-id (u/random-string 8)
        msg (doto (Message.) (.setBody message-text) (.setStanzaId stanza-id))]
    (db/store-stanza stanza-id)
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
                           (do (log/infof "Creating new connection for %s" sender)
                               (new-private-conn gw-xmpp-addr gw-xmpp-passwd gw-xmpp-room sender)))]
        (send-as conn muc sender message-text)
        (swap! connections assoc uid [conn muc]))
      (recur))))
