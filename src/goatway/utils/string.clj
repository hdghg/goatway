(ns goatway.utils.string)

(defn random-string
  "Generate string of random characters with given length"
  [len]
  (reduce str (take len (repeatedly #(rand-nth (map char (range 97 123)))))))
