(ns goatway.channels.xmpp.transformer
  (:require [clojure.core.async :refer [chan go <! >!]]
            [gram-api.hl :as hl]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

(def puppets-to-tg-users (atom {}))

(defn convert-highlight
  "Convert nick highlight to telegram-way: @nick"
  [message [puppet tg-user]]
  (str/replace message puppet tg-user))

(defn convert-highlights
  "Change text message in a way to highlight telegram users with @ symbol"
  [message-text]
  (reduce convert-highlight message-text @puppets-to-tg-users))

(defn transformer-chan
  "Formats message received from xmpp and sends it to out channel"
  [in-chan]
  (let [out-chan (chan)]
    (go (loop []
          (try
            (let [{:keys [stanza-id body nick] :as all} (<! in-chan)
                  body-with-highlights (convert-highlights body)
                  escaped-body (hl/escape-markdown body-with-highlights)
                  out-text (format "*%s:* %s" nick escaped-body)]
              (log/infof "Adding out-text to stanza-id %s: %s" stanza-id out-text)
              (>! out-chan (assoc all :out-text out-text)))
            (catch Exception e
              (log/error e "Transformer error")))
          (recur)))
    out-chan))
