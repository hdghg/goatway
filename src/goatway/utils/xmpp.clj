(ns goatway.utils.xmpp
  (:require [clojure.tools.logging :as log]
            [goatway.runtime.db :as db])
  (:import (org.jivesoftware.smackx.muc MultiUserChat)
           (org.jivesoftware.smack XMPPException$XMPPErrorException XMPPException$StreamErrorException AbstractConnectionListener)
           (org.jivesoftware.smack.packet XMPPError$Condition)))

(defn join-muc
  "Fail-safe join to multiuserchat"
  [^MultiUserChat muc ^String nick]
  (loop [attempt 10 last-err (ref nil) try-nick nick]
    (if (neg? attempt)
      {:error last-err}
      (do (try
            (.join muc try-nick)
            (dosync (ref-set last-err nil))
            (catch XMPPException$XMPPErrorException e
              (if (= XMPPError$Condition/conflict (.getCondition (.getXMPPError e)))
                (log/warn (str "Nickname " try-nick " conflicts, trying another..."))
                (log/error (str "Unknown error " e)))
              (dosync (ref-set last-err e))))
          (if @last-err
            (recur (dec attempt) last-err (str nick "-" (- 10 attempt)))
            {:nick try-nick})))))

(defn create-listener
  ([muc sender error-callback]
   (let [nick (atom nil)]
     (proxy [AbstractConnectionListener] []
       (authenticated [_ _]
         (let [res (join-muc muc sender)
               as (:nick res)]
           (if-let [error (:error res)]
             (log/error error)
             (do (log/infof "smack: joined muc as %s" as)
                 (reset! nick as)
                 (swap! db/puppets conj as)))))
       (connectionClosedOnError [e]
         (swap! db/puppets disj @nick)
         (if (and
               (instance? XMPPException$StreamErrorException e)
               (= XMPPError$Condition/conflict (-> ^XMPPException$StreamErrorException e
                                                   .getStreamError .getCondition)))
           (log/warn "smack: %s closed connection with conflict")
           (log/debug e))
         (if error-callback (error-callback))))))
  ([muc sender]
    (create-listener muc sender nil)))
