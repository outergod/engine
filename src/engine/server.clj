(ns engine.server
  (:use [lamina.core]
        [clojure.string :only (blank? trim)])
  (:require [clojure.tools.logging :as log]
            [engine.server.socket-io :as socket-io]
            [engine.data.rope :as rope]
            [engine.data.buffer :as buffer]))

(def ^:dynamic *session*)

(defonce buffers
  (agent {"*scratch*" (buffer/cursor "This is the scratch buffer." 0)}
         :validator #(and (map? %1)
                          (and (every? string? (keys %1))
                               (every? buffer/cursor? (vals %1))))
         :error-mode :continue
         :error-handler (fn [_ e]
                          (log/error "Attempted to set buffers agent to illegal state:" e))))

(defn send-buffer [buffer state]
  (send buffers assoc buffer state))

(defn server [socket event & args]
  (log/debug (format "received %s: %s" event args))
  (when-let [fun (ns-resolve 'engine.server (symbol event))]
    (binding [*session* (:session socket)]
      (apply fun args))))

(defn load-buffer [name]
  (let [cursor (@buffers name),
        rope @cursor,
        [row column] (rope/translate rope (buffer/pos cursor))]
    [(str rope) {:row row :column column}]))

(defn synchronized-insert-sequence [buffer cursor s]
  (send-buffer buffer (buffer/insert cursor s))
  ["self-insert-command" {:text s}])

(defn synchronized-delete< [buffer cursor]
  (send-buffer buffer (buffer/backward-delete cursor))
  ["backward-delete-char"])

(defn synchronized-delete> [buffer cursor]
  (send-buffer buffer (buffer/forward-delete cursor))
  ["delete-char"])

(defn synchronized-cursor-left [buffer cursor]
  (send-buffer buffer (buffer/backward-char cursor))
  ["backward-char"])

(defn synchronized-cursor-up [buffer cursor]
  (let [state (buffer/previous-line cursor)]
    (send-buffer buffer state)
    (let [[row column] (rope/translate @state (buffer/pos state))]
      ["move-to-position" {:row row :column column}])))

(defn synchronized-cursor-right [buffer cursor]
  (send-buffer buffer (buffer/forward-char cursor))
  ["forward-char"])

(defn synchronized-cursor-down [buffer cursor]
  (let [state (buffer/next-line cursor)]
    (send-buffer buffer state)
    (let [[row column] (rope/translate @state (buffer/pos state))]
      ["move-to-position" {:row row :column column}])))

(defn bitmask-seq [& xs]
  (zipmap (iterate (partial * 2) 1) xs))

(defn flagfn [& xs]
  (fn [n]
    (let [bitmask (apply bitmask-seq xs)]
      (->> (filter (complement #(zero? (bit-and n %))) (keys bitmask))
           (select-keys bitmask) vals set))))

(def modifier-keys (flagfn :ctrl :alt :shift))

(defn keyboard [hash-id key key-code buffer]
  (let [key (trim key), cursor (@buffers buffer),
        modifiers (modifier-keys hash-id)]
    (zipmap ["command" "args"]
            (cond (and (:alt modifiers) (= "x" key)) ["execute-extended-command"]
                  key-code
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
                  (not (blank? key)) (synchronized-insert-sequence buffer cursor key)
                  :default ["noop"]))))

(defn synchronized-mouse-left [buffer cursor position]
  (send-buffer buffer (buffer/goto-char cursor (rope/translate @cursor (:row position) (:column position))))
  ["move-to-position" position])

(defn mouse [type button position buffer]
  (let [cursor (@buffers buffer)]
    (zipmap ["command" "args"]
            (if (and (= type "mousedown") (= button 0))
              (synchronized-mouse-left buffer cursor position)
              ["noop"]))))
