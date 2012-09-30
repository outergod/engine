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
(ns engine.test.data.rope
  (:use midje.sweet clojure.test.tap)
  (:require [engine.data.rope :as rope]
            [engine.data.cursor :as cursor])
  (:import [clojure.lang Ref]))

(background (around :facts (let [s "Hello World!"
                                 c (cursor/cursor s)]
                             ?form)))

(fact "A cursor is a cursor and has a rope reference"
  (cursor/cursor? c) => true
  (instance? Ref (cursor/buffer c)) => true
  (-> c cursor/buffer deref rope/rope?) => true)

(fact "Looking at the end of the rope will not fail, but yield a special token"
  (-> c (cursor/goto-char (count s)) cursor/looking-at) => :end)

(fact "Iterating over each char resembles the original string"
  (->> (iterate cursor/forward-char c)
       (take-while #(not (cursor/end? %)))
       (map cursor/looking-at)) => (seq s))

(fact "Iterating over each char resembles the original string, all reverse"
  (->> (iterate cursor/backward-char (-> c cursor/end-of-buffer cursor/backward-char))
       (take-while #(not (cursor/start? %)))
       (map cursor/looking-at)) => (drop-last (reverse s)))

(fact "Going beyond of bounds drops back to the closest bound"
  (-> c (cursor/goto-char -10) cursor/pos) => 0
  (-> c (cursor/goto-char (inc (count s))) cursor/pos) => 12)

(fact "Deleting characters equal to the length of the rope yields an empty cursor"
  (-> c (cursor/forward-delete (count s)) cursor/buffer deref str) => ""
  (-> c cursor/end-of-buffer (cursor/backward-delete (count s)) cursor/buffer deref str) => "")

(fact "Going forward word-wise always jumps behind the current or next word"
  (-> (cursor/cursor "Foo Bar") cursor/forward-word cursor/pos) => 3
  (-> (cursor/cursor "Foo Bar") cursor/forward-word cursor/forward-word cursor/pos) => 7
  (-> (cursor/cursor "Foo   Bar") cursor/forward-word cursor/pos) => 3
  (-> (cursor/cursor "Foo   Bar") cursor/forward-word cursor/forward-word cursor/pos) => 9)

(fact "Going backward word-wise always jumps to the beginning of the current or preceding word"
  (-> (cursor/cursor "Foo Bar" 7) cursor/backward-word cursor/pos) => 4
  (-> (cursor/cursor "Foo Bar" 7) cursor/backward-word cursor/backward-word cursor/pos) => 0
  (-> (cursor/cursor "Foo   Bar" 9) cursor/backward-word cursor/pos) => 6
  (-> (cursor/cursor "Foo   Bar" 9) cursor/backward-word cursor/backward-word cursor/pos) => 0)

(fact "Corner cases of word-wise movement don't cause errors"
  (-> (cursor/cursor "") cursor/backward-word cursor/pos) => 0
  (-> (cursor/cursor "") cursor/forward-word cursor/pos) => 0
  (-> (cursor/cursor "Foo" 0) cursor/backward-word cursor/pos) => 0
  (-> (cursor/cursor "Foo" 3) cursor/forward-word cursor/pos) => 3)

(fact "The distance of two cursors works in both directions"
  (cursor/delta c c) => 0
  (cursor/delta c (cursor/cursor s 12)) => 12
  (cursor/delta (cursor/cursor s 12) c) => 12)
