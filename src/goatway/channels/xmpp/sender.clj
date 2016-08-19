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
      (let [{:keys [gw-tg-api gw-tg-chat out-text]} (<! in-chan)
            uid {:api gw-tg-api :chat gw-tg-chat}
            queue (get-stored uid)
            {new-queue :queue} (hl/enqueue-message {:api-key gw-tg-api :chat_id gw-tg-chat
                                                    :text    out-text :queue queue})]
        (when (nil? queue) (store uid new-queue)))
      (recur))))
