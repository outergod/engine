(ns engine.server
  (:use [lamina.core]
        [engine.server.socket-io :as socket-io]
        [clojure.tools.logging :as log]))

(defn server [socket event & args]
  (log/debug (format "received %s: %s" event args)))
