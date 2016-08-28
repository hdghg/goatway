(ns goatway.goat
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [gram-api.hl :as hl]
            [goatway.channels.xmpp.normalizer :as xmpp-normalizer]
            [goatway.channels.xmpp.filter :as xmpp-filter]
            [goatway.channels.tg.normalizer :as tg-normalizer]
            [goatway.channels.tg.executor :as tg-executor]
            [goatway.channels.tg.formatter :as tg-formatter]
            [goatway.channels.tg.splitter :as tg-splitter]
            [goatway.channels.xmpp.transformer :as xmpp->tg-transformer]
            [goatway.channels.xmpp.sender :as sender-to-tg]
            [clojure.core.async :as async]
            [goatway.utils.xmpp :as xmpp-u]
            [goatway.utils.string :as u]
            [goatway.runtime.db :as db])
  (:import (org.jivesoftware.smack SmackConfiguration PacketCollector)
           (org.jivesoftware.smack.tcp XMPPTCPConnection XMPPTCPConnectionConfiguration)
           (org.jivesoftware.smackx.muc MultiUserChatManager)
           (org.jivesoftware.smack.roster Roster)))

(defn smack->tg-pipe
  "Create series of pipes that flow messages from xmpp muc to telegram chat"
  []
  (let [in-chan (async/chan)
        normalizer-out (xmpp-normalizer/normalizer-chan in-chan)
        filter-out (xmpp-filter/filter-chan normalizer-out)
        transformer-out (xmpp->tg-transformer/transformer-chan filter-out)]
    (sender-to-tg/sender-chan transformer-out)
    in-chan))

(defn tg->smack-pipe
  "Create series of pipes that flow messages from telegram chat to xmpp muc"
  []
  (let [in-chan (async/chan)
        executor-out (tg-executor/executor-chan in-chan)
        normalizer-out (tg-normalizer/normalizer-chan executor-out)
        formatter-out (tg-formatter/formatter-to-xmpp normalizer-out)]
    (tg-splitter/split-send formatter-out)
    in-chan))

(defn start-smack-gw
  "Start smack gateway in background thread"
  [{:keys [gw-xmpp-addr gw-xmpp-passwd gw-xmpp-room gw-tg-api gw-tg-chat gw-xmpp-ignored]}]
  (log/info "smack: starting...")
  (SmackConfiguration/setDefaultPacketReplyTimeout 30000)
  (Roster/setRosterLoadedAtLoginDefault false)
  (let [[login server] (str/split gw-xmpp-addr #"@" 2)
        config (-> (XMPPTCPConnectionConfiguration/builder)
                   (.setUsernameAndPassword login gw-xmpp-passwd)
                   (.setServiceName server)
                   (.setResource (u/random-string 16))
                   (.build))
        conn (XMPPTCPConnection. config)
        mucm (MultiUserChatManager/getInstanceFor conn)
        muc (.getMultiUserChat mucm gw-xmpp-room)
        collector (.createPacketCollector conn (PacketCollector/newConfiguration))
        in-chan (smack->tg-pipe)
        ignored (if gw-xmpp-ignored (into #{} (str/split gw-xmpp-ignored #";")) #{})]
    (.addConnectionListener
      conn
      (xmpp-u/create-listener
        muc {:full_name login} (fn [] (log/info "smack: reconnecting") (.connect conn))))
    (-> conn .connect .login)
    (future (while true
              (let [next-elem {:gw-tg-api gw-tg-api :gw-tg-chat gw-tg-chat
                               :stanza    (.nextResultBlockForever collector)
                               :muc       muc :gw-xmpp-addr gw-xmpp-addr
                               :ignored   ignored}]
                (async/>!! in-chan next-elem))))
    [conn muc]))

(defn start-telegram-gw
  "Start telegram gateway in background thread"
  [{:keys [gw-tg-api gw-tg-chat xmpp-conn xmpp-muc gw-xmpp-addr gw-xmpp-passwd gw-xmpp-room]}]
  (log/info "telegram: starting...")
  (let [in-chan (tg->smack-pipe)]
    (future
      (loop [api-and-offset {:api-key gw-tg-api}]
        (let [next-result (hl/next-update api-and-offset)
              new-api-and-offset next-result]
          (async/>!! in-chan {:xmpp-conn    xmpp-conn :xmpp-muc xmpp-muc
                              :gw-xmpp-addr gw-xmpp-addr :gw-xmpp-passwd gw-xmpp-passwd
                              :gw-xmpp-room gw-xmpp-room
                              :result next-result
                              :chat_id gw-tg-chat :gw-tg-api gw-tg-api})
          (recur new-api-and-offset))))))

(defn start-goat
  "Start gateways"
  [all]
  (if-let [gw-db-conn (:gw-db-conn all)]
    (db/create-schema gw-db-conn)
    (if-let [gw-db-url (:gw-db-url all)]
      (db/create-schema {:connection-uri gw-db-url})
      (log/info "Database not configured, runnind in amnesia mode")))
  (let [[xmpp-conn xmpp-muc] (start-smack-gw all)]
    (start-telegram-gw (assoc all :xmpp-muc xmpp-muc :xmpp-conn xmpp-conn))))
