(ns engine.data.buffer
  (:use engine.data.mode
        [engine.server.command :only (command)]
        [engine.data.util])
  (:require [engine.data.rope :as rope]
            [engine.data.cursor :as cursor]
            [clojure.tools.logging :as log])
  (:import [engine.data.cursor Cursor]
           [clojure.lang IDeref Agent]))

(defprotocol IBuffer
  (trans [buffer actionfn] [buffer actionfn transfn])
  (inputfn [buffer]))

(defrecord Buffer [name ^Cursor cursor updatefn keymapfn mode file]
  IBuffer
  (trans [this actionfn transfn]
    (let [pre-state [@cursor (cursor/pos cursor)],
          state (dosync (let [state (-> cursor cursor/sanitize actionfn)]
                          (updatefn (Buffer. name state updatefn keymapfn mode file))
                          [@state (cursor/pos state)])),
          [row column] (apply rope/translate state)]
      {:response (conj (or (transfn pre-state state name) [])
                       (command "move-to-position" :row row :column column))}))
  (trans [this actionfn] (trans this actionfn (fn [& _] nil)))

  (inputfn [this]
    (keymapfn (partial trans this)))

  IDeref
  (deref [_] @cursor))

(defmethod print-method Buffer [buffer writer]
  (print-simple (format "#<Buffer %s -> %s>" (:name buffer) (pr-str (:cursor buffer))) writer))

(defn sender [^Agent buffers name]
  (fn [state]
    (send buffers assoc name state)))

(defn buffer [name updatefn & {:keys [cursor keymapfn mode file]
                               :or {cursor (cursor/cursor "" 0) keymapfn fundamental-mode-keymap}}]
  (Buffer. name cursor updatefn keymapfn mode file))


(defn buffer? [x]
  (instance? Buffer x))

(defn create-buffer [^Agent buffers name & args]
  (send buffers assoc name (apply buffer name (sender buffers name) args))
  (@buffers name))

(defn loader [^Agent buffers & opts]
  (fn [[name] _]
    (when-not (@buffers name)
      (apply create-buffer buffers name opts))
    (let [cursor (get-in @buffers [name :cursor]),
          rope @cursor,
          [row column] (rope/translate rope (cursor/pos cursor))]
      {:response [(split-lines (str rope)) {:row row :column column}]})))

(defn buffers
  ([state]
     (agent state
            :validator #(and (map? %1)
                             (and (every? string? (keys %1))
                                  (every? buffer? (vals %1)))),
            :error-mode :continue,
            :error-handler (fn [_ e]
                             (log/error "Attempted to set buffers agent to illegal state:" e))))
  ([]
     (let [buffers (buffers {}),
           name "*scratch*"]
       (create-buffer buffers "*scratch*" :cursor (cursor/cursor "This is the scratch buffer." 0))
       buffers)))
