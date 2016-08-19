(ns goatway.channels.xmpp.transformer
  (:require [clojure.core.async :refer [chan go <! >!]]
            [gram-api.hl :as hl]))

(defn transformer-chan
  "Formats message received from xmpp and sends it to out channel"
  [in-chan]
  (let [out-chan (chan)]
    (go
      (loop []
        (let [{:keys [body nick] :as all} (<! in-chan)]
          (if body
            (>! out-chan (assoc all :out-text (format "*%s:* %s" nick (hl/escape-markdown body))))))
        (recur)))
    out-chan))
