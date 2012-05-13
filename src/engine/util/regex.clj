(ns engine.util.regex
  (:import [java.util.regex Matcher Pattern]))

(defn re-pos
  "The matching position ranges of all regex matches"
  ([^java.util.regex.Matcher m]
     (for [match (repeatedly #(re-find m)) :while match]
       [(.start m) (.end m) (.hitEnd m)]))
  ([^java.util.regex.Pattern re s]
     (let [m (re-matcher re s)]
       (re-pos m))))

