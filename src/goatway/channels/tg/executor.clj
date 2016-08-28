(ns goatway.channels.tg.executor
  (:require [clojure.core.async :refer [chan go <! >!]]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [gram-api.hl :as hl]
            [goatway.runtime.db :as db])
  (:import (org.jivesoftware.smackx.muc MultiUserChat)
           (org.jxmpp.util XmppStringUtils)
           (org.jivesoftware.smack AbstractXMPPConnection)
           (org.jivesoftware.smackx.vcardtemp.packet VCard)
           (org.jivesoftware.smackx.vcardtemp VCardManager)))

(defn my-command [text]
  (or (str/starts-with? text "/who") (str/starts-with? text "/settings")))

(defn other-bot-command [message]
  (if message
    (let [first-word (first (str/split message #" " 2))]
      (and (str/starts-with? message "/") (str/includes? first-word "@")))
    false))

(defn query-vcard [^AbstractXMPPConnection conn ^MultiUserChat muc for]
  (try
    (let [^VCard vcard (.loadVCard (VCardManager/getInstanceFor conn) (str (.getRoom muc) "/" for))
          fn (.getField vcard "FN")
          nn (str (.getNickName vcard))]
      (format "Full name: %s, NickName: %s" fn nn))
    (catch Exception e
      (log/warnf e "Exception while getting vcard for %s" for)
      (format "Can't get vcard for %s" for))))


(defn exec [command ^AbstractXMPPConnection xmpp-conn ^MultiUserChat xmpp-muc chat_id gw-tg-api]
  (let [[first-word second-word] (str/split command #"[ ]" 2)
        [command _] (str/split first-word #"[@]" 2)]
    (if (= command "/who")
      (if second-word
        (hl/send-message-cycled {:api-key gw-tg-api :chat_id chat_id
                                 :text    (query-vcard xmpp-conn xmpp-muc second-word)})
        (let [ans (->> (.getOccupants xmpp-muc)
                       (map (fn [^String s] (XmppStringUtils/parseResource s)))
                       (filter (fn [nick] (not (get @db/puppets nick))))
                       (str/join "\n"))]
          (hl/send-message-cycled {:api-key gw-tg-api :chat_id chat_id :text ans}))
        ))))

(defn executor-chan
  "Create channel that filled by info about sender and message type"
  [in-chan]
  (let [out (chan)]
    (go
      (loop []
        (let [{:keys [xmpp-conn xmpp-muc chat_id gw-tg-api] :as next-message} (<! in-chan)
              text (get-in next-message [:result :body "result" 0 "message" "text"])]
          (log/infof "Next message text: %s" text)
          (if text
            (if (my-command text)
              (do (log/info "Text contains one of my commands, executing")
                  (exec text xmpp-conn xmpp-muc chat_id gw-tg-api))
              (if (not (other-bot-command text))
                (do (log/info "Text doesn't look like any bot command, passing")
                    (>! out next-message))
                (log/info "Text is a command to another bot, not passing")))
            (do (log/info "Not text in message, passing")
                (>! out next-message))))
        (recur)))
    out))
