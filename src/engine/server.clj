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
(def send-buffer (buffer/sender buffers))
(def load-buffer (buffer/loader buffers))
(def syncfn (syncer buffers send-buffer))
(def fundamental-keymap (fundamental-mode-keymap syncfn))

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

(defn keyboard [[hash-id key key-code buffer] state]
  (let [key (trim key),
        input (disj (conj (modifier-keys hash-id)
                          (or (key-codes key-code) key))
                    nil ""),
        keymap (or (state :keymap) fundamental-keymap)]
    (log/debug (format "Input interpreted as %s" input))
    (if-let [handler (keymap input)]
      (binding [*keymap* keymap] (handler buffer))
      (if (or key-code (blank? key))
        {:state {:keymap nil}}
        ((syncfn (insertfn key) command-insert) buffer)))))

(defn synchronized-mouse-left [buffer cursor position]
  (send-buffer buffer (cursor/goto-char cursor (rope/translate @cursor (:row position) (:column position))))
  {:response (command "move-to-position" position)})

(defn mouse [[type button position buffer] _]
  (let [cursor (@buffers buffer)]
    (when (and (= type "mousedown") (= button 0))
      (synchronized-mouse-left buffer cursor position))))
