(ns engine.server
  (:use lamina.core
        [clojure.string :only (blank? trim)]
        [engine.server input command]
        engine.data.mode)
  (:require [clojure.tools.logging :as log]
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
                        (log/debug (format "Broadcasting %s" (command-load buffer)))
                        (enqueue broadcast-channel (command-load buffer)))))))

(defn- load-response [buffer]
  {:commands (drop 2 (command-load buffer))})

(def load-buffer (buffer/loader buffers load-response))
(def load-minibuffer (buffer/loader buffers load-response :mode "minibuffer" :keymapfn minibuffer-mode-keymap))
(defn activate-minibuffer [[name {:keys [prompt args]}] _]
  (buffer/trans (@buffers name) (insertfn (str prompt args)) command-insert))

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
          (log/debug (format "Broadcasting %s" broadcasts))
          (doseq [command broadcasts] (enqueue broadcast-channel command)))
        (or commands (command "noop"))))))

(defn connect [socket]
  (let [receiver (channel)]
    (receive-all receiver #(socket-io/send-event socket (first %) (rest %)))
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
