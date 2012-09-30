;;;; Engine - cursor.clj
;;;; Copyright (C) 2012  Alexander Kahl <e-user@fsfe.org>
;;;; This file is part of Engine.
;;;; Engine is free software; you can redistribute it and/or modify it
;;;; under the terms of the GNU Affero General Public License as
;;;; published by the Free Software Foundation; either version 3 of the
;;;; License, or (at your option) any later version.
;;;;
;;;; Engine is distributed in the hope that it will be useful,
;;;; but WITHOUT ANY WARRANTY; without even the implied warranty of
;;;; MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
;;;; GNU General Public License for more details.
;;;;
;;;; You should have received a copy of the GNU General Public License
;;;; along with this program.  If not, see <http://www.gnu.org/licenses/>.
(ns engine.data.cursor
  (:use engine.util.regex)
  (:require [engine.data.rope :as rope]
            [clojure.string :as string]
            [clojure.tools.logging :as log])
  (:import [engine.data.rope Rope]
           [clojure.lang Ref IDeref]))

(defprotocol ICursor
  "Cursor implementations define methods that operate against positions in a 
  rope."
  (buffer [c] "Rope reference of cursor")
  (pos [c] "Current linear position of cursor")
  (pos-2d [c] "Current rectangular position of cursor")
  (looking-at [c] "Character that is currently being looked at")
  (purge [c] "Purge contents of cursor")
  (conj-history [c entry] "Cursor with history entry added")
  (goto-char [c n] [c row column] "Cursor with new position")
  (forward-char [c] "Cursor with position plus one, if possible")
  (backward-char [c] "Cursor with position minus one, if possible")
  (next-line [c] "Cursor with position in next line, if possible")
  (previous-line [c] "Cursor with position in previous line, if possible")
  (move-end-of-line [c] "Cursor with position at end of current line")
  (move-beginning-of-line [c] "Cursor with position at beginning of current line")
  (insert [c s] "Insert string in underlying rope at current position")
  (forward-delete [c] [c n] "Delete n characters starting from current position in the underlying rope")
  (backward-delete [c] [c n] "Delete n characters before current position in the underlying rope")
  (forward [c f] "Cursor with position at first match of f against reverse leaf nodes of rope, starting in reverse from current position")
  (backward [c f] "Cursor with position at first match of f against leaf nodes of rope, starting from current position")
  (sanitize [c] "Cursor with position guaranteed to be in bounds (0 to rope length)")
  (conclude [c] "Vector of cursor with new history and the split up history as second element")
  (start? [c] "Are we looking at the beginning of the rope?")
  (end? [c] "Are we looking at the end of the rope?"))

; necessary forward declaration
(deftype Cursor [^Ref root ^long position history])

(defn cursor?
  "Is x a Cursor?"
  [x]
  (instance? Cursor x))

(defmulti cursor
  "New Cursor from rope reference"
  (fn [obj & _] (type obj)))

(defmethod cursor Ref
  ([r pos history]
     (Cursor. r (min pos (count @r)) history))
  ([r pos]
     (cursor r pos []))
  ([r] (cursor r 0)))

(defrecord Cursor [^Ref root ^long position history]
  ICursor
  (buffer [_] root)
  (pos [_] position)
  (pos-2d [_] (rope/translate @root position))
  (looking-at [_]
    (if (= (count @root) position)
      :end
      (nth @root position)))
  (purge [_]
    (cursor ""))
  (conj-history [this entry]
    (assoc this :history (conj history entry)))
  
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
         (let [entry [:delete (rope/translate @root position) (rope/translate @root end)]]
           (alter root rope/delete position end)
           (conj-history this entry))))))
  (forward-delete [this]
    (forward-delete this 1))
  (backward-delete [this n]
    (dosync
     (if (zero? position)
       this
       (let [start (max 0 (- position n)),
             entry [:delete (rope/translate @root start) (rope/translate @root position)]]
         (alter root rope/delete start position)
         (conj-history (cursor root start history) entry)))))
  (backward-delete [this]
    (backward-delete this 1))

  (insert [this s]
    (dosync (alter root rope/insert position s))
    (conj-history (cursor root (+ position (count s)) history)
                  [:add (count s)]))

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

  (conclude [this]
    [(dissoc this :history) history])

  (start? [_]
    (= 0 position))

  (end? [_]
    (= (count @root) position))

  IDeref
  (deref [_] @root))

(defn report-rope-error
  "Default rope ref error handler for cursors"
  [_ e]
  (log/error "Attempted to set rope reference to illegal state:" e))

(defmethod cursor Rope
  ([r pos history]
     (Cursor. (ref r :error-mode :continue :error-handler report-rope-error)
              (min pos (count r)) history))
  ([r pos]
     (cursor r pos []))
  ([r] (cursor r 0)))

(defmethod cursor String
  ([s pos history]
     (cursor (rope/string->rope s) pos history))
  ([s pos]
     (cursor s pos []))
  ([s] (cursor s 0)))

(defmethod print-method Cursor [cursor writer]
  (print-simple (format "#<Cursor %d -> %s>" (pos cursor) (pr-str @cursor)) writer))

(defn- part-match
  "Match string against regular expression, evaluating to the end of the last match

  If end? is false and the end of string is hit (i.e. $, not just the last char
  before), literal true is returned to denote that scanning can continue againts
  subsequent partial strings.

  If back? is true, perform a reverse scan. The regular expression has to be
  reversed manually, however."
  ([re s end? back?]
     (let [[s f] (if back? [(string/reverse s) #(- (count s) %)] [s identity]),
           m (re-matcher re s), [_ n hit-end?] (last (re-pos m))]
       (if hit-end?
         (if end? (f (count s)) true)
         (and n (f n)))))
  ([re s end?]
     (part-match re s end? false)))

(defn forward-word
  "Cursor with position at the end of the current or next word"
  [cursor]
  (dosync
   (-> cursor (forward #(part-match #"^\s+" %1 %2)) (forward #(part-match #"^\S+" %1 %2)))))

(defn backward-word
  "Cursor with position at the beginning of the current or next word"
  [cursor]
  (dosync
   (-> cursor (backward #(part-match #"^\s+" %1 %2 true)) (backward #(part-match #"^\S+" %1 %2 true)))))

(defn delta
  "Distance between two cursors"
  [cursor1 cursor2]
  (->> [cursor1 cursor2] (map pos) (apply -) Math/abs))

(defn delta-apply
  "Apply deltafn against cursor and movefn applied to cursor"
  [cursor movefn deltafn]
  (dosync (deltafn cursor (delta cursor (movefn cursor)))))

(defn forward-kill-word
  "Kill one word forward

  Deletes the rest of the current word under cursor, or the whole next word if
  the current character is whitespace or the beginning of a word."
  [cursor]
  (delta-apply cursor forward-word forward-delete))

(defn backward-kill-word
  "Kill one word backward

  Deletes the preceding characters of the current word under cursor, or the
  whole previous word if the current character is whitespace or the end of a
  word."
  [cursor]
  (delta-apply cursor backward-word backward-delete))

(defn kill-line
  "Delete the rest of the current line

  Or just the current character if it is a newline."
  [cursor]
  (delta-apply cursor (if (= \newline (looking-at cursor)) forward-char move-end-of-line) forward-delete))

(defn beginning-of-buffer 
  "Move cursor to the beginning of the rope"
  [c]
  (cursor (buffer c) 0))

(defn end-of-buffer
  "Move cursor to the end of the rope"
  [c]
  (let [root (buffer c)]
   (cursor root (count @root))))
