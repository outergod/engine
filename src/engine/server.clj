(ns engine.server
  (:use [lamina.core]
        [engine.server.socket-io :as socket-io]
        [clojure.tools.logging :as log]
        [clojure.string :only [blank?]]))

(defn server [socket event & args]
  (log/debug (format "received %s: %s" event args))
  (when-let [fun (ns-resolve 'engine.server (symbol event))]
    (apply fun args)))

(defn load-buffer [name] ; TODO
  ["Test" {:row 0 :column 0}])

(defn keyboard [hash-id key key-code buffer-name]
  (zipmap ["command" "args"]
          (cond (and (nil? key-code)
                     (not (blank? key)))
                ["self-insert-command" {:text key}] ; TODO
                ; TODO
                :default
                ["noop" {}])))
