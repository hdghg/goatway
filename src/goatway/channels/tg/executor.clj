(ns goatway.channels.tg.executor
  (:require [clojure.core.async :refer [chan go <! >!]]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [gram-api.hl :as hl]
            [goatway.runtime.db :as db])
  (:import (org.jivesoftware.smackx.muc MultiUserChat)
           (org.jxmpp.util XmppStringUtils)))

(defn my-command [text]
  (or (str/starts-with? text "/who") (str/starts-with? text "/settings")))

(defn other-bot-command [message]
  (if message
    (let [first-word (first (str/split message #" " 2))]
      (and (str/starts-with? message "/") (str/includes? first-word "@")))
    false))

(defn exec [command ^MultiUserChat xmpp-muc chat_id gw-tg-api]
  (let [first-word (first (str/split command #"[ @]" 2))]
    (if (= first-word "/who")
      (let [ans (->> (.getOccupants xmpp-muc)
                     (map (fn [^String s] (XmppStringUtils/parseResource s)))
                     (filter (fn [nick] (not (get @db/puppets nick))))
                     (str/join "\n"))]
        (hl/send-message-cycled {:api-key gw-tg-api :chat_id chat_id :text ans})))))

(defn executor-chan
  "Create channel that filled by info about sender and message type"
  [in-chan]
  (let [out (chan)]
    (go
      (loop []
        (let [{:keys [xmpp-muc chat_id gw-tg-api] :as next-message} (<! in-chan)
              text (get-in next-message [:result :body "result" 0 "message" "text"])]
          (log/infof "Next message text: %s" text)
          (if text
            (if (my-command text)
              (do (log/info "Text contains one of my commands, executing")
                  (exec text xmpp-muc chat_id gw-tg-api))
              (if (not (other-bot-command text))
                (do (log/info "Text doesn't look like any bot command, passing")
                    (>! out next-message))
                (log/info "Text is a command to another bot, not passing")))
            (do (log/info "Not text in message, passing")
                (>! out next-message))))
        (recur)))
    out))
