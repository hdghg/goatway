(ns goatway.utils.tg)

(defn create-name [json]
  (let [first_name (get json "first_name")
        last_name (get json "last_name")]
    {:full_name (if last_name (str first_name " " last_name) first_name)
     :username  (get json "username")
     :id        (get json "id")}))
