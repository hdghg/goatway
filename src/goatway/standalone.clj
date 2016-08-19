(ns goatway.standalone
  (:gen-class)
  (:require [environ.core :refer [env]]
            [gram-api.hl :as hl]
            [goatway.goat :as goat]
            [goatway.config.logging :as logging]))

(defn chat-discovery [api-key]
  (println "You entered chat discovery mode. Idea of this to find out valid chat_id that goatway
will reside in. Now ask Botfather to set privacy mode of your bot to disabled (command /setprivacy),
after that invite your bot to group or supergroup and type some messages there. These messages
will be printed by goatway to console alongside theirs chat_id. After discovering correct chat_id,
terminate goatway and set environment variable GW_TG_CHAT to that value. Then start goatway again")
  (loop [result {:api-key api-key}]
    (let [next-result (hl/next-update result)]
      (if-let [error (:error next-result)]
        (println error)
        (do (println "chat:" (get-in next-result [:body "result" 0 "message" "chat" "id"])
                     "message:" (get-in next-result [:body "result" 0 "message" "text"]))
            (recur next-result))))))

(defn -main
  "Entry point to gateway-bot if it used as standalone project"
  [& _]
  (logging/replace-jul)
  (let [sa-params (select-keys env [:gw-tg-api :gw-tg-chat :gw-xmpp-ignored
                                    :gw-xmpp-addr :gw-xmpp-passwd :gw-xmpp-room])
        {:keys [gw-tg-api gw-tg-chat gw-xmpp-addr gw-xmpp-passwd gw-xmpp-room]} env]
    (if (some nil? [gw-tg-api gw-xmpp-passwd gw-xmpp-room gw-xmpp-addr])
      (println "To use Goatway as standalone executable, please set following environment vars:
      GW_TG_API:      Api key of telegram bot that will transfer messages (Meet botfather)
      GW_XMPP_ADDR:   Xmpp address for goatway
      GW_XMPP_PASSWD: Xmpp password for goatway
      GW_XMPP_ROOM:   Xmpp multi-user chat. Bot will receive all messages from this room and
                      send it to GW_TG_CHAT and vise versa

      GW_TG_CHAT:     (Non-mandatory) If not set, bot will enter discovery mode. It will help
                      you discover id of chat. If set properly, goatway will use it same as
                      GW_XMPP_ROOM")
      (if (nil? gw-tg-chat)
        (chat-discovery gw-tg-api)
        (goat/start-goat sa-params)))))
