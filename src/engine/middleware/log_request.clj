(ns engine.middleware.log-request
  (:use [clojure.tools.logging :only (debug)]
        clj-logging-config.log4j)
  (:require [clojure.java.io :as io]))

(def access-out (io/file "access.log"))

(defn log [handler]
  (fn [request]
    (with-logging-config [:root {:pattern "%d %-5p [%t]: %m%n" :level :debug :out access-out}]
      (debug (format "Current request is %s" request)))
    (handler request)))
