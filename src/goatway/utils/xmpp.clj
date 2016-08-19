(ns goatway.utils.xmpp
  (:require [clojure.tools.logging :as log])
  (:import (org.jivesoftware.smackx.muc MultiUserChat)
           (org.jivesoftware.smack XMPPException$XMPPErrorException)
           (org.jivesoftware.smack.packet XMPPError$Condition)))

(defn join-muc
  "Fail-safe join to multiuserchat"
  [^MultiUserChat muc ^String nick]
  (loop [attempt 10 last-err (ref nil) try-nick nick]
    (if (neg? attempt)
      last-err
      (do (try
            (.join muc try-nick)
            (dosync (ref-set last-err nil))
            (catch XMPPException$XMPPErrorException e
              (if (= XMPPError$Condition/conflict (.getCondition (.getXMPPError e)))
                (log/warn (str "Nickname " try-nick " conflicts, trying another..."))
                (log/error (str "Unknown error " e)))
              (dosync (ref-set last-err e))))
          (if @last-err
            (recur (dec attempt) last-err (str nick "-" (- 10 attempt))))))))
