(ns engine.server
  (:use lamina.core
        [clojure.string :only (blank? trim)])
  (:require [clojure.tools.logging :as log]
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
                          (log/error "Attempted to set buffers agent to illegal state:" e))))

(defn send-buffer [buffer state]
  (send buffers assoc buffer state))

(defn position-map [pos]
  (zipmap [:row :column] pos))

(defn command
  ([fn] {:command fn})
  ([fn args] {:command fn :args args}))

(defn rope-delta [before after]
  (->> [before after] (sort-by second) (map #(apply rope/translate %)) (map position-map)))

(defn command-insert [before after]
  (let [[_ pos1] before, [root pos2] after]
    [(command "insert-text" {:position (position-map (rope/translate root pos1))
                             :text (str (rope/report root pos1 pos2))})]))

(defn command-delete-forward [before after]
  (let [delta (->> [before after] (map (comp count first)) (apply -)),
        [root pos] before]
    [(command "delete-range" (rope-delta before [root (+ pos delta)]))]))

(defn command-delete-backward [before after]
  [(command "delete-range" (rope-delta before after))])

(defn server [socket event & args]
  (when-let [fun (ns-resolve 'engine.server (symbol event))]
    (let [session-agent (:session socket),
          {:keys [response state]} (fun args @session-agent)]
      (when state (send-off session-agent into [state]))
      (log/debug (format "Response is %s" response))
      (or response (command "noop")))))

(defn load-buffer [[name] _]
  (let [cursor (@buffers name),
        rope @cursor,
        [row column] (rope/translate rope (buffer/pos cursor))]
    {:response [(str rope) {:row row :column column}]}))

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

(defn insertfn [s]
  #(buffer/insert % s))

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
  (keymap {#{:backspace} (syncfn buffer/backward-delete command-delete-backward),
           #{:return} (syncfn (insertfn "\n") command-insert),
           #{:space} (syncfn (insertfn " ") command-insert),
           #{:shift :space} (aliasfn #{:space}),
           #{:cursor-left} (syncfn buffer/backward-char),
           #{:ctrl "b"} (syncfn buffer/backward-char),
           #{:cursor-up} (syncfn buffer/previous-line),
           #{:ctrl "p"} (syncfn buffer/previous-line),
           #{:cursor-right} (syncfn buffer/forward-char),
           #{:ctrl "f"} (syncfn buffer/forward-char),
           #{:cursor-down} (syncfn buffer/next-line),
           #{:ctrl "n"} (syncfn buffer/next-line),
           #{:ctrl "d"} (syncfn buffer/forward-delete command-delete-forward),
           #{:delete} (aliasfn #{:ctrl "d"}),
           #{:home} (syncfn buffer/move-beginning-of-line),
           #{:ctrl "a"} (syncfn buffer/move-beginning-of-line),
           #{:end} (syncfn buffer/move-end-of-line),
           #{:ctrl "e"} (syncfn buffer/move-end-of-line),
           #{:ctrl :cursor-right} (syncfn buffer/forward-word),
           #{:alt "f"} (syncfn buffer/forward-word),
           #{:ctrl :cursor-left} (syncfn buffer/backward-word),
           #{:alt "b"} (syncfn buffer/backward-word),
           #{:alt "d"} (syncfn buffer/forward-kill-word command-delete-forward),
           #{:alt :backspace} (syncfn buffer/backward-kill-word command-delete-backward),
           #{:alt :shift ","} (syncfn buffer/beginning-of-buffer),
           #{:alt :shift "."} (syncfn buffer/end-of-buffer),
           #{:alt "x"} (fn [& _] {:response (command "execute-extended-command")}),
           #{:ctrl "x"} (fn [& _] {:state {:keymap keymap}})}))

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
  (send-buffer buffer (buffer/goto-char cursor (rope/translate @cursor (:row position) (:column position))))
  {:response (command "move-to-position" position)})

(defn mouse [[type button position buffer] _]
  (let [cursor (@buffers buffer)]
    (when (and (= type "mousedown") (= button 0))
      (synchronized-mouse-left buffer cursor position))))
