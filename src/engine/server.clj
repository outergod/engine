;;;; Engine - server.clj
;;;; Copyright (C) 2012  Alexander Kahl <e-user@fsfe.org>
;;;; This file is part of Engine.
;;;; Engine is free software; you can redistribute it and/or modify it
;;;; under the terms of the GNU Affero General Public License as
;;;; published by the Free Software Foundation; either version 3 of the
;;;; License, or (at your option) any later version.
;;;;
;;;; Engine is distributed in the hope that it will be useful,
;;;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;;;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;;;; GNU General Public License for more details.
;;;;
;;;; You should have received a copy of the GNU General Public License
;;;; along with this program.  If not, see <http://www.gnu.org/licenses/>.
(ns engine.server
  (:refer-clojure :exclude [load-file])
  (:use lamina.core
        [clojure.string :only (blank? trim)]
        engine.server.input
        [engine.data command mode])
  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [engine.server.socket-io :as socket-io]
            [engine.data.rope :as rope]
            [engine.data.cursor :as cursor]
            [engine.data.buffer :as buffer]))

(def broadcast-channel (permanent-channel))
(receive-all broadcast-channel (fn [_]))

(defonce buffers
  (buffer/buffers (fn [_ _ _ state]
                    (let [buffer (-> (meta state) :buffer state)]
                      (when-not (nil? (:change buffer)) ; don't broadcast new buffer state
                        (enqueue broadcast-channel (command-load buffer)))))))

(defn- load-response [buffer]
  {:commands (drop 2 (command-load buffer))})

(def load-buffer (buffer/loader buffers load-response))
(def load-minibuffer (buffer/loader buffers load-response :mode "minibuffer" :keymapfn minibuffer-mode-keymap))
(defn activate-minibuffer [[name {:keys [prompt args]}] _]
  (buffer/trans (@buffers name) (insertfn (str prompt args))))

; TODO
(defn load-file [[path] _]
  (when-not (@buffers path)
    (let [contents (if (.exists (io/file path)) (slurp path) "")]
      (buffer/create-buffer buffers path :cursor (-> contents rope/string->rope (cursor/cursor 0)) :file path))) ; FIXME rope buds
  (commands ["load" path])) 

(defn server [socket event & args]
  (when-let [fun (ns-resolve 'engine.server (symbol event))]
    (if (socket-io/reserved-event-names event)
      (fun socket)
      (let [session-agent (:session socket),
            {:keys [commands state]} (fun args @session-agent)
            {:keys [commands broadcasts]} (group-by #(if (:broadcast (meta %)) :broadcasts :commands) commands)]
        (when state (send-off session-agent into [state]))
        (log/debug (format "Response is %s" commands))
        (when broadcasts
          (doseq [command broadcasts] (enqueue broadcast-channel command)))
        commands))))

(defn connect [socket]
  (let [receiver (channel)]
    (receive-all receiver (fn [[name & args]]
                            (log/debug (format "Broadcasting %s %s" name args))
                            (socket-io/send-event socket name args)))
    (siphon broadcast-channel receiver)))

(defn keyboard [[hash-id key key-code name] state]
  (let [key (trim key),
        input (disj (conj (modifier-keys hash-id)
                          (or (key-codes key-code) key))
                    nil ""),
        buffer (@buffers name),
        keymap (or (state :keymap) (buffer/inputfn buffer))]
    (log/debug (format "Input interpreted as %s" input))
    (if-let [handler (keymap input)]
      (binding [*keymap* keymap] (handler))
      (if (or key-code (blank? key))
        {:state {:keymap nil}}
        (buffer/trans buffer (insertfn key))))))

(defn synchronized-mouse-left [name position]
  (let [{:keys [row column]} position]
    (buffer/trans (@buffers name) #(cursor/goto-char % row column))))

(defn mouse [[type button position name] _]
  (when (and (= type "mousedown") (= button 0))
    (synchronized-mouse-left name position)))
