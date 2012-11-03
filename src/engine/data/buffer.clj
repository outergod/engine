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
(ns engine.data.buffer
  (:refer-clojure :exclude [sync])
  (:use [engine.data mode util])
  (:require [engine.data.cursor :as cursor]
            [clojure.tools.logging :as log])
  (:import [engine.data.cursor Cursor]
           [clojure.lang IDeref Agent]
           java.util.UUID))

(defprotocol IBuffer
  "Buffer protocol"
  (sync [buffer]))

(defrecord Buffer [^Agent owner id ^Cursor cursor name]
  IBuffer
  (sync [this]
    (send owner #(assoc % id this)))
  
  IDeref
  (deref [_] @cursor))

(defmethod print-method Buffer [buffer writer]
  (print-simple (format "#<Buffer %s -> %s>" (:name buffer) (pr-str (:cursor buffer))) writer))

(defn buffer?
  "Is x a buffer?"
  [x]
  (instance? Buffer x))

(defn buffer
  "Buffer from given arguments"
  [^Agent owner id ^Cursor cursor name]
  (Buffer. owner id cursor name))

(defn- new-id
  "New random, unique ID"
  []
  (str (UUID/randomUUID)))

(defn create-buffer
  "Buffer from given arguments that is also stored in buffers agent"
  [^Agent buffers & args]
  (let [id (new-id)]
   (await (sync (apply buffer buffers id args)))
   (@buffers id)))
 
(defn buffers
  "New buffers agent from state

  Or preloaded with scratch buffer, if no state given."
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
       (create-buffer buffers (cursor/cursor "This is the scratch buffer." 0) "*scratch*")
       buffers)))
