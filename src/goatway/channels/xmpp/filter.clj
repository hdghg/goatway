(ns goatway.channels.xmpp.filter
  (:require [clojure.core.async :refer [chan go <! >!]]
            [amalloy.ring-buffer :as ring-buffer]
            [clojure.tools.logging :as log]))

(def ignored-local (atom #{}))

(def sent (atom (ring-buffer/ring-buffer 20)))

(defn matches
  "Returns true when sender not ignored and not himself"
  [{:keys [nick ignored body]}]
  (not (or (get ignored nick)
           (get @ignored-local nick)
           (not body))))

(defn filter-chan
  "Filters messages that not ignored and was not sent before to out channel"
  [in-chan]
  (let [out (chan)]
    (go (loop []
          (let [next (<! in-chan)]
            (when
              (and (matches next) (not (some #{(:stanza-id next)} @sent)))
              (do (swap! sent into [(:stanza-id next)])
                  (>! out next)))
            (recur))))
    out))
