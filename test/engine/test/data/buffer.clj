;;;; Engine - buffer.clj
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
(ns engine.test.data.buffer
  (:use midje.sweet clojure.test.tap)
  (:require [engine.data.rope :as rope]
            [engine.data.cursor :as cursor]
            [engine.data.buffer :as buffer]))

(defmacro private [sym]
  `(ns-resolve 'engine.data.buffer '~sym))

(fact "IDs are actually unique"
  (let [ids (repeatedly 10 (private new-id))]
    (= (count ids) (count (set ids))) => true))
