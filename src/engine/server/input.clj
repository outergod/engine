(ns engine.server.input
  (:use [clojure.string :only (blank? trim)]
        engine.server.command)
  (:require [clojure.tools.logging :as log]
            [engine.server.socket-io :as socket-io]
            [engine.data.cursor :as cursor]
            [engine.data.buffer :as buffer]))

(def ^:dynamic *keymap*)
(defn aliasfn [key]
  #(apply (*keymap* key) %&))

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

(defn keymap [map]
  (into map
        {#{:ctrl "g"} {:response (command "noop"),
                       :state {:keymap nil}}}))
