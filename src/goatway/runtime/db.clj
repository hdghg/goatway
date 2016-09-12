(ns goatway.runtime.db
  (:require [amalloy.ring-buffer :as ring-buffer]
            [clojure.java.jdbc :as j]
            [clojure.tools.logging :as log]))


(def puppets (atom #{}))

(def connection-uri (atom nil))

(def sent-stanzas (atom (ring-buffer/ring-buffer 50)))

(defn create-schema [db-uri]
  (reset! connection-uri db-uri)
  (j/execute! db-uri "create table if not exists sent_stanzas
  (id serial, stanza varchar(255))")
  (j/execute! db-uri "create table if not exists tg_settings
  (id serial, tg_user_id bigint, key varchar(255), value varchar(255))"))


(defn store-stanza [stanza-id]
  (if-let [db @connection-uri]
    (future
      (try (do
             (j/insert! db :sent_stanzas {:stanza stanza-id})
             (j/execute! db ["delete from sent_stanzas where id not in
                              (select id from sent_stanzas order by id desc limit 50)"]))
           (catch Exception e (log/error e))))
    (swap! sent-stanzas into [stanza-id])))

(defn stanza-not-stored [stanza-id]
  (if-let [db @connection-uri]
    (->> (format "select count(1) count from sent_stanzas where stanza = '%s'" stanza-id)
         (j/query db) (first) (:count) (zero?))
    (not-any? #{stanza-id} @sent-stanzas)))

(defn list-tg-settings [tg_user_id]
  (if-let [db @connection-uri]
    (j/query db (format "select key, value from tg_settings where tg_user_id = %s" tg_user_id) )
    nil))

(defn set-prop [tg_user_id key value]
  (when-let [db @connection-uri]
    (j/execute! db ["delete from tg_settings where tg_user_id = ? and key = ?"
                    tg_user_id key])
    (j/insert! db :tg_settings {:tg_user_id tg_user_id :key key :value value})))
