(ns engine.data.buffer
  (:use [engine.data mode util])
  (:require [engine.data.rope :as rope]
            [engine.data.cursor :as cursor]
            [clojure.tools.logging :as log])
  (:import [engine.data.cursor Cursory]
           [clojure.lang IDeref Agent]))

(defprotocol IBuffer
  "Buffer protocol"
  (trans [buffer] [buffer actionfn] [buffer actionfn transfn])
  (inputfn [buffer] "Input keymap function for buffer"))

(defrecord Buffer [name ^Cursory cursor updatefn change mode file]
  IBuffer
  (trans [this actionfn transfn]
    (let [pre-state [@cursor (cursor/pos cursor)]]
      (dosync (let [state (-> cursor cursor/sanitize actionfn),
                    {:keys [change response]} (transfn pre-state [@state (cursor/pos state)] name)]
                (updatefn (assoc this :cursor state :change change))
                response))))
  (trans [this actionfn] (trans this actionfn (voidfn {:change false})))
  (trans [this] this)

  (inputfn [this]
    ((keymapfn mode) (partial trans this)))
  
  IDeref
  (deref [_] @cursor))

(defmethod print-method Buffer [buffer writer]
  (print-simple (format "#<Buffer %s -> %s>" (:name buffer) (pr-str (:cursor buffer))) writer))

(defn buffer
  "New buffer from given arguments

spec keymapfn: fn [syncfn] -> map | syncfn: fn [updatefn transfn] -> cursor"
  [name updatefn & {:keys [cursor keymapfn mode file]
                    :or {cursor (cursor/cursor "" 0), mode :fundamental-mode}}]
  (Buffer. name cursor updatefn nil mode file))

(defn buffer?
  "Is x a Buffer?"
  [x]
  (instance? Buffer x))

(defn create-buffer
  "New buffer from given arguments that is also stored in buffers agent"
  [^Agent buffers name & args]
  (let [update (fn [state]
                 (send buffers #(-> % (assoc name state) (vary-meta assoc :buffer name))))]
   (await (update (apply buffer name update args)))
   (@buffers name)))

(defn loader
  "Function that creates new buffers on demand and evaluates to their state

spec callback: fn [buffer] -> command"
  [^Agent buffers callback & opts]
  (fn [[name] _]
    (callback
     (or (@buffers name)
         (apply create-buffer buffers name :cursor (cursor/cursor "" 0) opts)))))

(defn buffers
  "New buffers agent from state

Or only with scratch buffer, if not given."
  ([watchfn state]
     (let [buffers (agent state
                          :validator #(and (map? %1)
                                           (and (every? string? (keys %1))
                                                (every? buffer? (vals %1)))),
                          :error-mode :continue,
                          :error-handler (fn [_ e]
                                           (log/error "Attempted to set buffers agent to illegal state:" e)))]
       (add-watch buffers nil watchfn)
       buffers))
  ([watchfn]
     (let [buffers (buffers watchfn {})]
       (create-buffer buffers "*scratch*" :cursor (cursor/cursor "This is the scratch buffer." 0))
       buffers)))
