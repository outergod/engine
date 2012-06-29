(ns engine.data.cursor
  (:use engine.util.regex)
  (:require [engine.data.rope :as rope]
            [clojure.string :as string])
  (:import [engine.data.rope Rope]
           [clojure.lang Ref IDeref]))

(defprotocol Cursory
  (buffer [cursor])
  (pos [cursor])
  (pos-2d [cursor])
  (looking-at [cursor])
  (goto-char [cursor n] [cursor row column])
  (forward-char [cursor])
  (backward-char [cursor])
  (next-line [cursor])
  (previous-line [cursor])
  (move-end-of-line [cursor])
  (move-beginning-of-line [cursor])
  (insert [cursor s])
  (forward-delete [cursor] [cursor n])
  (backward-delete [cursor] [cursor n])
  (forward [cursor f])
  (backward [cursor f])
  (sanitize [cursor]))

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
  (pos-2d [_] (rope/translate @root position))
  (looking-at [_]
    (nth @root position))

  (goto-char [_ n]
    (sanitize (cursor root n)))
  (goto-char [this row column]
    (goto-char this (rope/translate @root row column)))
  (forward-char [this]
    (if (< position (count @root))
      (cursor root (inc position))
      this))
  (backward-char [this]
    (if (> position 0)
      (cursor root (dec position))
      this))
  (next-line [this]
    (let [[row column] (pos-2d this),
          last-row (-> @root rope/measure :lines)]
      (if (= row last-row) ; already at last row
        (cursor root (count @root))
        (let [row+1-pos (rope/translate @root (+ row 1) 0),
              row+2-pos (if (= (inc row) last-row)
                          (inc (count @root))
                          (rope/translate @root (+ row 2) 0))]
          (cursor root (rope/translate @root (inc row) (min column (dec (- row+2-pos row+1-pos)))))))))
  (previous-line [this]
    (let [[row column] (pos-2d this)]
      (if (zero? row)
        (cursor root 0)
        (let [row-pos (rope/translate @root row 0),
              row-1-pos (rope/translate @root (dec row) 0)]
          (cursor root (rope/translate @root (dec row) (min column (dec (- row-pos row-1-pos)))))))))

  (move-end-of-line [this]
    (let [[row _] (pos-2d this),
          last-row (-> @root rope/measure :lines)]
      (cursor root (if (= row last-row)
                     (count @root)
                     (dec (rope/translate @root (+ row 1) 0))))))
  (move-beginning-of-line [this]
    (let [[row _] (pos-2d this)]
      (cursor root (rope/translate @root row 0))))

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

  (insert [this s]
    (dosync (alter root rope/insert position s))
    (cursor root (+ position (count s))))

  (forward [this f]
    (let [[_ r] (rope/split-merge @root position)]
      (if-let [i (rope/index-of r f)]
        (cursor root (+ position i))
        this)))
  (backward [this f]
    (let [[r _] (rope/split-merge @root position)]
      (if-let [i (rope/last-index-of r f)]
        (cursor root i)
        this)))

  (sanitize [this]
    (cond (neg? position) (cursor root 0)
          (> position (count @root)) (cursor root (count @root))
          :default this))

  IDeref
  (deref [_] @root))

(defmethod cursor Rope [root position]
  (Cursor. (ref root :error-mode :continue) (min position (count root))))

(defmethod cursor String [s position]
  (cursor (rope/string->rope s) position))

(defmethod print-method Cursor [cursor writer]
  (print-simple (format "#<Cursor %d -> %s>" (pos cursor) (pr-str @cursor)) writer))

(defn part-match
  ([re s end? back?]
     (let [[s f] (if back? [(string/reverse s) #(- (count s) %)] [s identity]),
           m (re-matcher re s), [_ n hit-end?] (last (re-pos m))]
       (if hit-end?
         (if end? (f (count s)) true)
         (and n (f n)))))
  ([re s end?]
     (part-match re s end? false)))

(defn forward-word [cursor]
  (dosync
   (-> cursor (forward #(part-match #"^\s+" %1 %2)) (forward #(part-match #"^\S+" %1 %2)))))

(defn backward-word [cursor]
  (dosync
   (-> cursor (backward #(part-match #"^\s+" %1 %2 true)) (backward #(part-match #"^\S+" %1 %2 true)))))

(defn delta [cursor1 cursor2]
  (->> [cursor1 cursor2] (map pos) (apply -) Math/abs))

(defn- kill-section [cursor movefn deletefn]
  (dosync (deletefn cursor (delta cursor (movefn cursor)))))

(defn forward-kill-word [cursor]
  (kill-section cursor forward-word forward-delete))

(defn backward-kill-word [cursor]
  (kill-section cursor backward-word backward-delete))

(defn kill-line [cursor]
  (kill-section cursor (if (= \newline (looking-at cursor)) forward-char move-end-of-line) forward-delete))

(defn beginning-of-buffer [c]
  (cursor (buffer c) 0))

(defn end-of-buffer [c]
  (let [root (buffer c)]
   (cursor root (count @root))))
