(ns engine.data.util)

(defn split-lines
  "Like clojure.string/split-lines, but doesn't mess up trailing empty lines"
  [s]
  (seq (.split #"\n" s -1)))
