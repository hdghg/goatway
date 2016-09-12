(ns goatway.channels.tg.normalizer
  (:require [clojure.core.async :refer [chan go <! >!]]
            [clojure.tools.logging :as log]
            [goatway.utils.tg :as tg-utils]
            [clojure.data.json :as json]))

(defn- detect-message-type
  "Make a guess about message type from it's content"
  [message]
  (cond
    (empty? message) :empty
    (get message "audio") :audio
    (get message "voice") :voice
    (get message "document") :document
    (get message "photo") :photo
    (get message "audio") :audio
    (get message "sticker") :sticker
    (get message "video") :video
    (get message "contact") :contact
    (get message "location") :location
    (get message "venue") :venue
    (get message "new_chat_participant") :new_chat_participant
    (get message "left_chat_participant") :left_chat_participant
    (get message "new_chat_title") :new_chat_title
    (get message "new_chat_photo") :new_chat_photo
    (get message "delete_chat_photo") :delete_chat_photo
    (get message "pinned_message") :pinned_message
    (get message "text") :text
    :else :unknown))

(defn- detect-type
  "Make a guess about message type from responce body"
  [body]
  (if (nil? body)
    :empty
    (if-let [message (get-in body ["result" 0 "message"])]
      [(detect-message-type message) (detect-message-type (get message "reply_to_message"))]
      :empty)))


(defn normalizer-chan
  "Create channel that filled by info about sender and message type"
  [in-chan]
  (let [out (chan)]
    (go
      (loop []
        (let [{:keys [result chat_id] :as all} (<! in-chan)
              error (:error result)
              body (:body result)]
          (log/infof "I take following data: ::result :body %s :error %s, :chat_id %s"
                     (json/write-str body) error chat_id)
          (if error
            (log/errorf "Error encountered: :error %s" error)
            (try
              (let [recv-chat_id (str (get-in body ["result" 0 "message" "chat" "id"]))
                    sender (tg-utils/create-name (get-in body ["result" 0 "message" "from"]))
                    type (detect-type body)]
                (if (= chat_id recv-chat_id)
                  (do (log/infof "I add following data: :sender %s :type %s" sender type)
                      (>! out (assoc all :sender sender :type type)))
                  (log/infof "Non-matching :recv-chat-id %s, expected :chat_id %s"
                             recv-chat_id chat_id)))
              (catch Exception e (log/error e)))))
        (recur)))
    out))
