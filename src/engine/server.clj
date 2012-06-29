(ns engine.server
  (:use lamina.core
        [clojure.string :only (blank? trim)]
        [engine.server input command] engine.data.mode)
  (:require [clojure.tools.logging :as log]
            [engine.server.socket-io :as socket-io]
            [engine.data.rope :as rope]
            [engine.data.cursor :as cursor]
            [engine.data.buffer :as buffer]))

(defonce buffers (buffer/buffers))
(def load-buffer (buffer/loader buffers))
(def load-minibuffer (buffer/loader buffers :mode "minibuffer" :keymapfn minibuffer-mode-keymap))

(def broadcast-channel (permanent-channel))
(receive-all broadcast-channel (fn [_]))

(defn server [socket event & args]
  (when-let [fun (ns-resolve 'engine.server (symbol event))]
    (if (socket-io/reserved-event-names event)
      (fun socket)
      (let [session-agent (:session socket),
            {:keys [response state]} (fun args @session-agent)
            {:keys [response broadcast]} (group-by #(if (:broadcast (meta %)) :broadcast :response) response)]
        (when state (send-off session-agent into [state]))
        (log/debug (format "Response is %s" response))
        (when broadcast
          (log/debug (format "Broadcasting %s" broadcast))
          (doseq [command broadcast] (enqueue broadcast-channel command)))
        (or response (command "noop"))))))

(defn connect [socket]
  (let [receiver (channel)]
    (receive-all receiver #(socket-io/send-event socket "broadcast" %))
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
        (buffer/trans buffer (insertfn key) command-insert)))))

(defn synchronized-mouse-left [name position]
  (let [{:keys [row column]} position]
    (buffer/trans (@buffers name) #(cursor/goto-char % row column))))

(defn mouse [[type button position name] _]
  (when (and (= type "mousedown") (= button 0))
    (synchronized-mouse-left name position)))
