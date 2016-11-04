(ns goatway.channels.xmpp.sender
  (:require [clojure.core.async :refer [chan go <! >!]]
            [gram-api.hl :as hl]
            [clojure.tools.logging :as log])
  (:import (java.util WeakHashMap)))

; Send to tg result from xmpp

(def queues (WeakHashMap.))

(defn- get-stored [uid]
  (.get ^WeakHashMap queues uid))

(defn- store [uid queue]
  (.put ^WeakHashMap queues uid queue))

(defn sender-chan
  "Consumes message from in-chan and sends it to telegram conference"
  [in-chan]
  (go
    (loop []
      (try
        (let [{:keys [gw-tg-api gw-tg-chat out-text stanza-id]} (<! in-chan)
              ;uid {:api gw-tg-api :chat gw-tg-chat}
              ;queue (get-stored uid)
              ]
          (hl/send-message-cycled {:api-key    gw-tg-api :chat_id gw-tg-chat
                                   :text       out-text
                                   :parse_mode "Markdown"})
          (log/infof "stanza %s: Message successfully enqueued" stanza-id)
          ;(when (nil? queue) (store uid new-queue))
          )
        (catch Exception e
          (log/error e "Exception while sending message to telegram")))
      (recur))))
