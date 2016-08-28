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
  (id serial, stanza varchar(255))"))


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
