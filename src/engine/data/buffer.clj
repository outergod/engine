(ns engine.data.buffer
  (:require [engine.data.rope :as rope])
  (:import [engine.data.rope Rope]
           [clojure.lang Ref IDeref]))

(defprotocol Cursory
  (buffer [cursor])
  (pos [cursor])
  (goto-char [cursor n])
  (forward-char [cursor])
  (backward-char [cursor])
  (next-line [cursor])
  (previous-line [cursor])
  (insert [cursor string])
  (forward-delete [cursor] [cursor n])
  (backward-delete [cursor] [cursor n]))

(deftype Cursor [^Ref root ^long position])

(defn cursor?
  "Is x a Cursor?"
  [x]
  (instance? Cursor x))

(defmulti cursor (fn [obj _] (type obj)))
(defmethod cursor Ref [root position]
  (Cursor. root (min position (count @root))))

(deftype Cursor [^Ref root ^long position]
  Cursory
  (buffer [_] root)
  (pos [_] position)

  (goto-char [_ n] (cursor root n))
  (forward-char [_] (cursor root (inc position)))
  (backward-char [_] (cursor root (dec position)))
  (next-line [_]
    (dosync 
     (let [[row column] (rope/translate @root position),
           last-row (-> @root rope/measure :lines)]
       (if (= row last-row) ; already at last row
         (cursor root (count @root))
         (let [row+1-pos (rope/translate @root (+ row 1) 0),
               row+2-pos (if (= (inc row) last-row)
                           (count @root)
                           (rope/translate @root (+ row 2) 0))]
           (cursor root (rope/translate @root (inc row) (min column (dec (- row+2-pos row+1-pos))))))))))
  (previous-line [_]
    (dosync 
     (let [[row column] (rope/translate @root position)]
       (if (zero? row)
         (cursor root 0)
         (let [row-pos (rope/translate @root row 0),
               row-1-pos (rope/translate @root (dec row) 0)]
           (cursor root (rope/translate @root (dec row) (min column (dec (- row-pos row-1-pos))))))))))

  (forward-delete [this n]
    (dosync 
     (let [length (count @root), end (min (+ position n) length)]
       (if (>= position length)
         this
         (do (alter root rope/delete position end)
             this)))))
  (forward-delete [this]
    (forward-delete this 1))
  (backward-delete [this n]
    (if (zero? position)
      this
      (let [start (max 0 (- position n))]
        (do (dosync (alter root rope/delete start position))
            (cursor root start)))))
  (backward-delete [this]
    (backward-delete this 1))

  (insert [this string]
    (dosync (alter root rope/insert position string))
    (cursor root (+ position (count string))))

  IDeref
  (deref [_] @root))

(defmethod cursor Rope [root position]
  (Cursor. (ref root :error-mode :continue) (min position (count root))))

(defmethod cursor String [s position]
  (cursor (rope/string->rope s) position))

(defmethod print-method Cursor [cursor writer]
  (print-simple (format "#<Cursor %d -> %s>" (pos cursor) (pr-str @cursor)) writer))
