(ns engine.server.input)

(defn bitmask-seq [& xs]
  (zipmap (iterate (partial * 2) 1) xs))

(defn flagfn [& xs]
  (fn [n]
    (let [bitmask (apply bitmask-seq xs)]
      (->> (filter (complement #(zero? (bit-and n %))) (keys bitmask))
           (select-keys bitmask) vals set))))

(def modifier-keys (flagfn :ctrl :alt :shift))

(def key-codes
  {8 :backspace,
   13 :return,
   32 :space,
   35 :end,
   36 :home,
   37 :cursor-left,
   38 :cursor-up,
   39 :cursor-right,
   40 :cursor-down,
   46 :delete})
