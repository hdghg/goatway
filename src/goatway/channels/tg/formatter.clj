(ns goatway.channels.tg.formatter
  (:require [clojure.core.async :refer [chan go <! >!]]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [clojure.string :as str]
            [goatway.utils.tg :as tg-utils]))

(defn join-info
  "Joins non-nil strings and puts result in square brackets"
  [& facts]
  (->> facts (filter identity) (str/join ", ") (format "[%s]")))

(defn forward-header [all]
  (let [forward_from (get all "forward_from")
        forward_from_chat (get all "forward_from_chat")]
    (cond
      forward_from (str "Forwarded from " (:full_name (tg-utils/create-name forward_from)) ":\n")
      forward_from_chat (format "Forwarded from %s %s:\n" (get forward_from_chat "type")
                                (get forward_from_chat "title"))
      :else "")))

(def handlers
  (atom {:sticker  (fn [_ _ message]
                     (let [sticker (get message "sticker")
                           forward-header (forward-header message)
                           size (format "%s b" (get sticker "file_size"))
                           g (format "%sx%s" (get sticker "width") (get sticker "height"))
                           emoji (get sticker "emoji")]
                       (format "%s%s %s" forward-header emoji (join-info g size))))
         :text     (fn [_ _ message] (format "%s%s" (forward-header message)
                                             (get message "text")))
         :photo    (fn [_ _ message]
                     (let [caption (get message "caption")
                           forward-header (forward-header message)
                           photos (get message "photo")
                           photo (last photos)
                           size (format "%s b" (get photo "file_size"))
                           g (format "%sx%s" (get photo "width") (get photo "height"))]
                       (format "%s%s %s" forward-header (or caption "photo") (join-info g size))))
         :empty    (fn [_ _ _] nil)
         :document (fn [_ _ message]
                     (let [document (get message "document")
                           forward-header (forward-header message)
                           caption (get message "caption")
                           file_name (get document "file_name")
                           size (str (get document "file_size") " b")]
                       (format "%s%s %s" forward-header (or caption "document")
                               (join-info file_name size))))
         :audio    (fn [_ _ message]
                     (let [audio (get message "audio")
                           forward-header (forward-header message)
                           size (format "size: %s b" (get audio "file_size"))
                           duration (format "duration: %ss" (get audio "duration"))
                           performer (if-let [p (get audio "performer")] (format "performer: %s" p))
                           title (if-let [t (get audio "title")] (format "title: %s" t))]
                       (format "%saudio %s" forward-header
                               (join-info title performer duration size))))
         :voice    (fn [_ _ message]
                     (let [voice (get message "voice")
                           forward-header (forward-header message)
                           size (format "size: %s b" (get voice "file_size"))
                           duration (format "duration: %ss" (get voice "duration"))]
                       (format "%svoice %s" forward-header (join-info duration size))))
         :video    (fn [_ _ message]
                     (let [video (get message "video")
                           forward-header (forward-header message)
                           size (format "size: %s b" (get video "file_size"))
                           duration (format "duration: %ss" (get video "duration"))
                           g (format "%sx%s" (get video "width") (get video "height"))]
                       (format "%svideo %s" forward-header (join-info g duration size))))
         :left_chat_participant
                   (fn [_ _ message]
                     (let [left (get message "left_chat_participant")
                           left_id (get left "id")
                           from_id (get-in message ["from" "id"])]
                       (if (= left_id from_id)
                         "/me left group"
                         (str "Removed participant: " (:full_name (tg-utils/create-name left))))))
         :new_chat_participant
                   (fn [_ _ message]
                     (let [new (get message "new_chat_participant")
                           new_id (get new "id")
                           from_id (get-in message ["from" "id"])]
                       (if (= new_id from_id)
                         "/me joined group"
                         (str "Added participant: " (:full_name (tg-utils/create-name new))))))

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
