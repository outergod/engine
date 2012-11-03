(ns engine.data.command
  (:use engine.data.util)
  (:require [engine.data.rope :as rope]
            [engine.data.cursor :as cursor]))

(defn position-map [pos]
  (zipmap [:row :column] pos))

(defn broadcasted [obj]
  (vary-meta obj assoc :broadcast true))

(defn commands [& specs]
  {:commands specs})

(defn trans-exit [before _ _]
  (let [[rope pos] before]
    (commands ["exit"])))

(defn command-load [buffer]
  (let [{:keys [name cursor change]} buffer,
        position (position-map (rope/translate @cursor (cursor/pos cursor)))]
    ["buffer-update" name (-> @cursor str split-lines) position change]))

(defn command-execute
  ([buffer command]
     (broadcasted ["execute-extended-command" (:name buffer) {:prompt "> " :args command}]))
  ([buffer]
     (command-execute buffer "")))

(defn state-keymap [keymap]
  {:state {:keymap keymap}})

(defn insertfn [s] #(cursor/insert % s))
