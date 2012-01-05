(ns engine.middleware.log-request
  (:use [clojure.tools.logging :only [debug]]))

(defn log [handler]
  (fn [request]
    (debug (format "Current request is %s" request))
    (handler request)))
