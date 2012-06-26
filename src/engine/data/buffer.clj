(ns engine.data.buffer
  (:use engine.data.mode)
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
                       {:command "move-to-position" :args {:row row :column column}})}))
  (trans [this actionfn] (trans this actionfn (fn [& _] nil)))

  (inputfn [this]
    (keymapfn (partial trans this)))

  IDeref
  (deref [_] @cursor))

(defmethod print-method Buffer [buffer writer]
  (print-simple (format "#<Buffer %s -> %s>" (:name buffer) (pr-str @ buffer)) writer))

(defn buffer [name ^Cursor cursor updatefn keymapfn]
  (Buffer. name cursor updatefn keymapfn nil nil))

(defn buffer? [x]
  (instance? Buffer x))

(defn sender [^Agent buffers name]
  (fn [state]
    (send buffers assoc name state)))

(defn loader [^Agent buffers]
  (fn [[name] _]
    (let [cursor (get-in @buffers [name :cursor]),
          rope @cursor,
          [row column] (rope/translate rope (cursor/pos cursor))]
      {:response [(str rope) {:row row :column column}]})))

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
       (send buffers assoc name
             (buffer name (cursor/cursor "This is the scratch buffer." 0) (sender buffers name) fundamental-mode-keymap))
       buffers)))
