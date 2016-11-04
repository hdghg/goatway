(ns goatway.channels.tg.splitter
  (:require [clojure.core.async :refer [chan go <! >!]]
            [clojure.string :as str]
            [goatway.utils.string :as u]
            [goatway.utils.xmpp :as xmpp-u]
            [goatway.runtime.db :as db]
            [clojure.tools.logging :as log]
            [gram-api.hl :as hl])
  (:import (org.jivesoftware.smack.tcp XMPPTCPConnection XMPPTCPConnectionConfiguration)
           (org.jivesoftware.smackx.muc MultiUserChatManager MultiUserChat)
           (org.jivesoftware.smack.packet Message)
           (org.jivesoftware.smack.sasl SASLErrorException SASLError)
           (org.jivesoftware.smack SmackException ConnectionConfiguration$SecurityMode AbstractXMPPConnection)
           (sun.security.provider.certpath SunCertPathBuilderException)))

(def connections (atom {}))

(defn new-conn
  ([xmpp-addr xmpp-passwd xmpp-room sender]
   (new-conn xmpp-addr xmpp-passwd xmpp-room sender ConnectionConfiguration$SecurityMode/ifpossible))
  ([xmpp-addr xmpp-passwd xmpp-room sender ^ConnectionConfiguration$SecurityMode sm]
   "Create new xmpp connection for given participant using given credentials"
   (let [[login server] (str/split xmpp-addr #"@" 2)
         config (-> (XMPPTCPConnectionConfiguration/builder)
                    (.setUsernameAndPassword login xmpp-passwd)
                    (.setSecurityMode sm)
                    (.setServiceName server)
                    (.setResource (u/random-string 16))
                    (.build))
         conn (XMPPTCPConnection. config)
         mucm (MultiUserChatManager/getInstanceFor conn)
         muc (.getMultiUserChat mucm xmpp-room)]
     (.addConnectionListener conn (xmpp-u/create-listener muc sender))
     (-> conn .connect .login)
     (log/infof "Created connection %s and muc %s" conn muc)
     [conn muc])))

(defn read-prop [settings-map key]
  (:value (first (filter #(= (:key %) key) settings-map))))

(defn read-private-xmpp-data [id]
  (let [user-settings (db/list-tg-settings id)
        private-xmpp-addr (read-prop user-settings "private-xmpp-addr")
        private-xmpp-passwd (read-prop user-settings "private-xmpp-passwd")]
    (if (and private-xmpp-addr private-xmpp-passwd)
      [private-xmpp-addr private-xmpp-passwd])))

(def unauth-err "Cannot join room with your credentials. Using default.")

(defn new-private-conn
  [xmpp-addr xmpp-passwd xmpp-room sender api-key]
  (if-let [[private-xmpp-addr private-xmpp-passwd] (read-private-xmpp-data (:id sender))]
    (do (log/infof "User %s uses private xmpp connection settings" sender)
        (try
          (new-conn private-xmpp-addr private-xmpp-passwd xmpp-room sender)
          (catch SASLErrorException e
            (if (= (.getSASLError (.getSASLFailure e)) SASLError/not_authorized)
              (do (log/warnf "User %s is not_authorised: %s" sender e)
                  (hl/send-message-cycled {:api-key api-key :chat_id (:id sender)
                                           :text    unauth-err}))
              (log/warnf "Cannot establish connection using private acc for user %s: %s" sender e))
            (new-conn xmpp-addr xmpp-passwd xmpp-room sender))
          (catch SmackException e
            (let [first-cause (.getCause e)
                  second-cause (when first-cause (.getCause first-cause))
                  third-cause (when second-cause (.getCause second-cause))]
              (log/debugf "First cause: %s" first-cause)
              (log/debugf "Second cause: %s" second-cause)
              (log/debugf "Thirs cause: %s" third-cause)
              (if (instance? SunCertPathBuilderException third-cause)
                (do (log/warnf "Private acc %s failed TLS validation, trying w/o TLS"
                               private-xmpp-addr)
                    (try
                      (new-conn private-xmpp-addr private-xmpp-passwd xmpp-room sender
                                ConnectionConfiguration$SecurityMode/disabled)
                      (log/infof "Private acc %s connected w/o TLS" private-xmpp-addr)
                      (catch Exception e
                        (log/warnf "Private acc %s failed w/o TLS, falling back to default %s"
                                   private-xmpp-addr e)
                        (new-conn xmpp-addr xmpp-passwd xmpp-room sender))))
                (do (log/warnf "Unknown instance of error %s" third-cause)
                    (new-conn xmpp-addr xmpp-passwd xmpp-room sender)))))
          (catch Exception e
            (log/warnf "Exception while using private acc for user %s: %s" sender e)
            (new-conn xmpp-addr xmpp-passwd xmpp-room sender))))
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

(defn- process [all]
  (try
    (let [{:keys [sender message-text gw-tg-api type result
                  gw-xmpp-addr gw-xmpp-passwd gw-xmpp-room]} all
          uid {:sender (:id sender) :api-key gw-tg-api}
          [conn muc] (or (@connections uid)
                         (do (log/infof "Creating new connection for %s" sender)
                             (new-private-conn gw-xmpp-addr gw-xmpp-passwd gw-xmpp-room sender
                                               gw-tg-api)))]
      (send-as conn muc sender message-text)
      (if (= :left_chat_participant (get type 0))
        (let [left-user (get-in result [:body "result" 0 "message" "left_chat_participant" "id"])]
          (when-let [[^AbstractXMPPConnection left-conn ^MultiUserChat left-muc]
                     (@connections {:sender left-user :api-key gw-tg-api})]
            (.leave left-muc)
            (.disconnect left-conn))))
      (swap! connections assoc uid [conn muc]))
    (catch Exception e (log/warn e))))

(defn split-send
  "Take message from channel and send it behalf :sender"
  [in-chan]
  (go
    (loop []
      (process (<! in-chan))
      (recur))))
