;;;; Engine - core.clj
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
(ns engine.core
  (:use aleph.http
        [ring.middleware.stacktrace :only (wrap-stacktrace)]
        [ring.middleware.session :only (wrap-session)]
        [net.cgrand.moustache :only (app)]
        [compojure.core :only (routes)])
  (:require [engine server]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [engine.middleware.log-request :as log-request]
            [engine.middleware.alias-request :as alias-request]
            [engine.server.socket-io :as socket-io]))

(defonce dispatch-table
  (agent {} :validator #(and (map? %1)
                             (and (every? string? (keys %1))
                                  (every? fn? (vals %1))))
         :error-mode :continue))

(defn server-dispatch [request]
  (when-let [handler (@dispatch-table (:uri request))]
    (handler request)))

(defn defdispatcher [uri handler]
  (send dispatch-table #(assoc %1 uri handler)))

(def dispatcher
  (app
   (wrap-stacktrace)
   (wrap-session {:cookie-name "engine"})
   (log-request/log)
   (socket-io/socket.io-handler engine.server/server)
   (alias-request/alias "/client/jquery.pnotify.js" "/client/pnotify/jquery.pnotify.min.js")
   (alias-request/alias "/client/jquery.pnotify.css" "/client/pnotify/jquery.pnotify.default.css")
   ["client" "ace" &] (app
                       ["theme" &] (route/resources "/" {:root "support/ace/build/src"})
                       [&] (route/resources "/" {:root "support/ace/lib/ace"}))
   ["client" "pilot" &] (route/resources "/" {:root "support/ace/lib/pilot"})
   ["client" "pnotify" &] (route/resources "/" {:root "support/pnotify"})
   ["client" &] (route/resources "/" {:root "client"})
   [&] (routes server-dispatch (route/not-found "Not found"))))

(defn start-server []
  (start-http-server (wrap-ring-handler (fn [& args]
                                          (apply dispatcher args)))
                     {:port 8080 :join? false :websocket true}))

(defonce server (start-server))

(defn restart-server []
  (when server (server))
  (def server (start-server)))
