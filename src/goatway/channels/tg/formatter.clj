(ns goatway.channels.tg.formatter
  (:require [clojure.core.async :refer [chan go <! >!]]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [clojure.string :as str]))

(defn join-info
  "Joins non-nil strings and puts result in square brackets"
  [& facts]
  (->> facts (filter identity) (str/join ", ") (format "[%s]")))

(def handlers
  (atom {:sticker  (fn [_ _ message]
                     (let [sticker (get message "sticker")
                           size (format "%s b" (get sticker "file_size"))
                           g (format "%sx%s" (get sticker "width") (get sticker "height"))
                           emoji (get sticker "emoji")]
                       (format "%s %s" emoji (join-info g size))))
         :text     (fn [_ _ message] (get message "text"))
         :photo    (fn [_ _ message]
                     (let [caption (get message "caption")
                           photos (get message "photo")
                           photo (last photos)
                           size (format "%s b" (get photo "file_size"))
                           g (format "%sx%s" (get photo "width") (get photo "height"))]
                       (format "%s %s" (or caption "photo") (join-info g size))))
         :empty    (fn [_ _ _] nil)
         :document (fn [_ _ message]
                     (let [document (get message "document")
                           caption (get message "caption")
                           file_name (get document "file_name")
                           size (get document "file_size")]
                       (format "%s %s" caption (join-info file_name size))))
         :audio    (fn [_ _ message]
                     (let [audio (get message "audio")
                           size (format "size: %s b" (get audio "file_size"))
                           duration (format "duration: %ss" (get audio "duration"))
                           performer (if-let [p (get audio "performer")] (format "performer: %s" p))
                           title (if-let [t (get audio "title")] (format "title: %s" t))]
                       (format "audio %s" (join-info title performer duration size))))
         :voice    (fn [_ _ message]
                     (let [voice (get message "voice")
                           size (format "size: %s b" (get voice "file_size"))
                           duration (format "duration: %ss" (get voice "duration"))]
                       (format "voice %s" (join-info duration size))))
         :video    (fn [_ _ message]
                     (let [video (get message "video")
                           size (format "size: %s b" (get video "file_size"))
                           duration (format "duration: %ss" (get video "duration"))
                           g (format "%sx%s" (get video "width") (get video "height"))]
                       (format "video %s" (join-info g duration size))))
         :left_chat_participant
                   (fn [_ _ message]
                     (let [left (get message "left_chat_participant")
                           left_id (get left "id")
                           from_id (get-in message ["from" "id"])]
                       (if (= left_id from_id)
                         "/me left group"
                         (str "Removed participant: "
                              (or (get left "username") (get left "first_name"))))))
         :new_chat_participant
                   (fn [_ _ message]
                     (let [new (get message "new_chat_participant")
                           new_id (get new "id")
                           from_id (get-in message ["from" "id"])]
                       (if (= new_id from_id)
                         "/me joined group"
                         (str "Added participant: "
                              (or (get new "username") (get new "first_name"))))))
         :forward_from
                   (fn [_ _ message]
                     (let [forward_from (get message "forward_from")
                           username (or (get forward_from "username")
                                        (get forward_from "first_name"))
                           text (get message "text")]
                       (format "forwarded from %s\n%s" username text)))
         }))

(defn unknown-formatter
  "Reports that format of message is not recognized"
  [_ t m]
  (log/warnf "Unknown type: %s message: %s" t (json/write-str m))
  nil)

(defn give-formatter
  "Returns function that can format message by message type (:text, :sticker etc.)"
  [type]
  (if-let [res (@handlers type)] res unknown-formatter))

(defn format-msg
  "Find function that can format message with given type"
  [api-key type message]
  ((give-formatter type) api-key type message))

(defn formatter-to-xmpp
  "Create channel that takes map of values from input channel. Message extracted from
  [:result :body \"result\" 0 \"message\", converted to text and passed to out channel."
  [in-chan]
  (let [out (chan)]
    (go
      (loop []
        (let [next (<! in-chan)
              api-key (:gw-tg-api next)
              type (:type next)
              [current-format replied-to-format] type]
          (log/infof "I take following data: :api-key %s :type %s" api-key type)
          (try
            (let [formatted (format-msg api-key current-format
                                        (get-in next [:result :body "result" 0 "message"]))
                  formatted-reply-to
                  (format-msg api-key replied-to-format
                              (get-in next [:result :body "result" 0 "message" "reply_to_message"]))
                  out-message (format (if formatted-reply-to "»%2$s\n%1$s" "%1$s")
                                      formatted formatted-reply-to)]
              (log/infof "I add following data: :message-text %s " out-message)
              (>! out (assoc next :message-text out-message)))
            (catch Exception e (log/errorf "Failed to format %s, exception: %s" next e))))
        (recur)))
    out))
