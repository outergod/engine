(ns engine.server
  (:use lamina.core
        [clojure.string :only (blank? trim)])
  (:require [clojure.tools.logging :as log]
            [clojure.stacktrace :as stacktrace]
            [engine.server.socket-io :as socket-io]
            [engine.data.rope :as rope]
            [engine.data.buffer :as buffer]))

(def ^:dynamic *keymap*)

(defonce buffers
  (agent {"*scratch*" (buffer/cursor "This is the scratch buffer." 0)}
         :validator #(and (map? %1)
                          (and (every? string? (keys %1))
                               (every? buffer/cursor? (vals %1)))),
         :error-mode :continue,
         :error-handler (fn [_ e]
                          (log/error "Attempted to set buffers agent to illegal state:" e)
                          (log/trace (with-out-str (stacktrace/print-stack-trace e))))))

(defn send-buffer [buffer state]
  (send buffers assoc buffer state))

(defn command
  ([fn] {:command fn})
  ([fn args] {:command fn :args args}))

(defn command-delete [before after]
  [(command "delete-range" (->> [before after] (sort-by second) (map #(apply rope/translate %)) (map #(zipmap [:row :column] %))))])

(defn server [socket event & args]
  (when-let [fun (ns-resolve 'engine.server (symbol event))]
    (let [session-agent (:session socket),
          {:keys [response state]} (fun args @session-agent)]
      (when state (send-off session-agent into [state]))
      (or response (command "noop")))))

(defn load-buffer [[name] _]
  (let [cursor (@buffers name),
        rope @cursor,
        [row column] (rope/translate rope (buffer/pos cursor))]
    {:response [(str rope) {:row row :column column}]}))

(defn synchronized-insert-sequence [buffer cursor s]
  (send-buffer buffer (buffer/insert cursor s))
  {:response (command "self-insert-command" {:text s})})

(defn synchronized-delete> [buffer cursor]
  (send-buffer buffer (buffer/forward-delete cursor))
  {:response (command "delete-char")})

(defn syncfn
  ([actionfn transfn]
     (fn [buffer]
       (let [cursor (@buffers buffer),
             pre-state [@cursor (buffer/pos cursor)],
             state (dosync (let [state (-> cursor buffer/sanitize actionfn)]
                             (send-buffer buffer state)
                             [@state (buffer/pos state)])),
             [row column] (apply rope/translate state)]
         {:response (conj (or (transfn pre-state state) [])
                          (command "move-to-position" {:row row :column column}))})))
  ([actionfn] (syncfn actionfn (fn [& _] nil))))

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

(defn aliasfn [key]
  #(apply (*keymap* key) %&))

(defn keymap [map]
  (into map
        {#{:ctrl "g"} {:response (command "noop"),
                       :state {:keymap nil}}}))

(def fundamental-keymap
  (keymap {#{:backspace} (syncfn buffer/backward-delete command-delete),
           #{:return} #(synchronized-insert-sequence % (@buffers %) "\n"),
           #{:space} #(synchronized-insert-sequence % (@buffers %) " "),
           #{:shift :space} (aliasfn #{:space}),
           #{:cursor-left} (syncfn buffer/backward-char),
           #{:ctrl "b"} (syncfn buffer/backward-char),
           #{:cursor-up} (syncfn buffer/previous-line),
           #{:ctrl "p"} (syncfn buffer/previous-line),
           #{:cursor-right} (syncfn buffer/forward-char),
           #{:ctrl "f"} (syncfn buffer/forward-char),
           #{:cursor-down} (syncfn buffer/next-line),
           #{:ctrl "n"} (syncfn buffer/next-line),
           #{:delete} #(synchronized-delete> % (@buffers %)),
           #{:ctrl "d"} #(synchronized-delete> % (@buffers %)),
           #{:home} (syncfn buffer/move-beginning-of-line),
           #{:ctrl "a"} (syncfn buffer/move-beginning-of-line),
           #{:end} (syncfn buffer/move-end-of-line),
           #{:ctrl "e"} (syncfn buffer/move-end-of-line),
           #{:ctrl :cursor-right} (syncfn buffer/forward-word),
           #{:alt "f"} (syncfn buffer/forward-word),
           #{:ctrl :cursor-left} (syncfn buffer/backward-word),
           #{:alt "b"} (syncfn buffer/backward-word),
           #{:alt "x"} (fn [& _] {:response (command "execute-extended-command")}),
           #{:ctrl "x"} (fn [& _] {:state {:keymap keymap}})}))

(defn keyboard [[hash-id key key-code buffer] state]
  (let [key (trim key), cursor (@buffers buffer),
        input (disj (conj (modifier-keys hash-id)
                          (or (key-codes key-code) key))
                    nil ""),
        keymap (or (state :keymap) fundamental-keymap)]
    (if-let [handler (keymap input)]
      (binding [*keymap* keymap] (handler buffer))
      (if (or key-code (blank? key))
        {:state {:keymap nil}}
        (synchronized-insert-sequence buffer cursor key)))))

(defn synchronized-mouse-left [buffer cursor position]
  (send-buffer buffer (buffer/goto-char cursor (rope/translate @cursor (:row position) (:column position))))
  {:response (command "move-to-position" position)})

(defn mouse [[type button position buffer] _]
  (let [cursor (@buffers buffer)]
    (when (and (= type "mousedown") (= button 0))
      (synchronized-mouse-left buffer cursor position))))
