(ns engine.server
  (:use [lamina.core]
        [clojure.string :only (blank? trim)])
  (:require [clojure.tools.logging :as log]
            [engine.server.socket-io :as socket-io]
            [engine.data.rope :as rope]
            [engine.data.buffer :as buffer]))

(defonce buffers
  (agent {"*scratch*" (buffer/cursor "This is the scratch buffer." 0)}
         :validator #(and (map? %1)
                          (and (every? string? (keys %1))
                               (every? buffer/cursor? (vals %1))))
         :error-mode :continue
         :error-handler (fn [_ e]
                          (log/error "Attempted to set buffers agent to illegal state:" e))))

(defn dobuffer [buffer state]
  (send buffers assoc buffer state))

(defn server [socket event & args]
  (log/debug (format "received %s: %s" event args))
  (when-let [fun (ns-resolve 'engine.server (symbol event))]
    (apply fun args)))

(defn load-buffer [name]
  (let [cursor (@buffers name),
        rope @cursor,
        [row column] (rope/translate rope (buffer/pos cursor))]
    [(str rope) {:row row :column column}]))

(defn synchronized-insert-sequence [buffer cursor s]
  (dobuffer buffer (buffer/insert cursor s))
  ["self-insert-command" {:text s}])

(defn synchronized-delete< [buffer cursor]
  (dobuffer buffer (buffer/backward-delete cursor))
  ["backward-delete-char"])

(defn synchronized-delete> [buffer cursor]
  (dobuffer buffer (buffer/forward-delete cursor))
  ["delete-char"])

(defn synchronized-cursor-left [buffer cursor]
  (dobuffer buffer (buffer/backward-char cursor))
  ["backward-char"])

(defn synchronized-cursor-up [buffer cursor]
  (let [state (buffer/previous-line cursor)]
    (dobuffer buffer state)
    (let [[row column] (rope/translate @state (buffer/pos state))]
      ["move-to-position" {:row row :column column}])))

(defn synchronized-cursor-right [buffer cursor]
  (dobuffer buffer (buffer/forward-char cursor))
  ["forward-char"])

(defn synchronized-cursor-down [buffer cursor]
  (let [state (buffer/next-line cursor)]
    (dobuffer buffer state)
    (let [[row column] (rope/translate @state (buffer/pos state))]
      ["move-to-position" {:row row :column column}])))

(defn keyboard [hash-id key key-code buffer]
  (let [key (trim key), cursor (@buffers buffer)]
    (zipmap ["command" "args"]
            (cond key-code
                  (case key-code
                    8 (synchronized-delete< buffer cursor)               ; backspace
                    13 (synchronized-insert-sequence buffer cursor "\n") ; return
                    32 (synchronized-insert-sequence buffer cursor " ")  ; space
                    37 (synchronized-cursor-left buffer cursor)          ; cursor left
                    38 (synchronized-cursor-up buffer cursor)            ; cursor up
                    39 (synchronized-cursor-right buffer cursor)         ; cursor right
                    40 (synchronized-cursor-down buffer cursor)          ; cursor down
                    46 (synchronized-delete> buffer cursor)              ; delete
                    ["noop"])
                  (not (blank? key))
                  (synchronized-insert-sequence buffer cursor key)
                  :default ["noop"]))))
