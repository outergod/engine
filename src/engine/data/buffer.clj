(ns engine.data.buffer
  (:require [engine.data.rope :as rope]
            [engine.data.cursor :as cursor]
            [clojure.tools.logging :as log]))

(defn buffers
  ([state]
     (agent state
            :validator #(and (map? %1)
                             (and (every? string? (keys %1))
                                  (every? cursor/cursor? (vals %1)))),
            :error-mode :continue,
            :error-handler (fn [_ e]
                             (log/error "Attempted to set buffers agent to illegal state:" e))))
  ([] (buffers {"*scratch*" (cursor/cursor "This is the scratch buffer." 0)})))

(defn sender [buffers]
  (fn [buffer state]
    (send buffers assoc buffer state)))

(defn loader [buffers]
  (fn [[name] _]
    (let [cursor (@buffers name),
          rope @cursor,
          [row column] (rope/translate rope (cursor/pos cursor))]
      {:response [(str rope) {:row row :column column}]})))
