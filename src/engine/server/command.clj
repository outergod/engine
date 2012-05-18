(ns engine.server.command
  (:require [engine.data.rope :as rope]
            [engine.data.cursor :as cursor]))

(defn command
  ([fn] {:command fn})
  ([fn args] {:command fn :args args}))

(defn position-map [pos]
  (zipmap [:row :column] pos))

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

(defn syncer [buffers sendfn]
  (fn syncfn
    ([actionfn transfn]
       (fn [buffer]
         (let [cursor (@buffers buffer),
               pre-state [@cursor (cursor/pos cursor)],
               state (dosync (let [state (-> cursor cursor/sanitize actionfn)]
                               (sendfn buffer state)
                               [@state (cursor/pos state)])),
               [row column] (apply rope/translate state)]
           {:response (conj (or (transfn pre-state state) [])
                            (command "move-to-position" {:row row :column column}))})))
    ([actionfn] (syncfn actionfn (fn [& _] nil)))))

(defn insertfn [s] #(cursor/insert % s))
