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

(defn trans-insert [before after _]
  (let [[rope1 pos1] before, [rope2 pos2] after]
    {:change {:action "insertText" :range {:start (position-map (rope/translate rope1 pos1)),
                                           :end (position-map (rope/translate rope2 pos2))}}}))

(defn trans-delete-forward [before after _]
  (let [[rope1 pos] before, [rope2 _] after,
        delta (- (count rope1) (count rope2))]
    {:change {:action "removeText", :range {:start (position-map (rope/translate rope1 pos)),
                                            :end (position-map (rope/translate rope1 (+ pos delta)))}}}))

(defn trans-delete-backward [before after _]
  (let [[rope1 pos1] before, [rope2 pos2] after]
    {:change {:action "removeText" :range {:start (position-map (rope/translate rope2 pos2)),
                                           :end (position-map (rope/translate rope1 pos1))}}}))

(defn trans-exit [before _ _]
  (let [[rope pos] before]
    {:response (commands ["exit"]),
     :change {:action "removeText" :range {:start (position-map [0 0]),
                                           :end (position-map (rope/translate rope pos))}}}))

(defn command-load [buffer]
  (let [{:keys [name cursor change]} buffer,
        position (position-map (rope/translate @cursor (cursor/pos cursor)))]
    ["buffer-update" name (-> @cursor str split-lines) position change]))

(defn insertfn [s] #(cursor/insert % s))
