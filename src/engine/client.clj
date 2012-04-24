(ns engine.client
  (:use [engine.core :only (defdispatcher)]
        [clojure.string :only (split join)])
  (:require [clojure.java.io :as io])
  (:import (java.util Date Locale TimeZone)
           java.text.SimpleDateFormat))

(defn rfc-1123-format []
  (doto (SimpleDateFormat. "E, dd MMM yyyy HH:mm:ss z" Locale/US)
    (.setTimeZone (TimeZone/getTimeZone "UTC"))))

(defn not-modified-since? [{headers :headers} last-modified]
  (when-let [modified-since (:if-modified-since headers)]
    (not (.. (rfc-1123-format) (parse modified-since) (before last-modified)))))

(defn unchanged-handler [body]
  (let [date (Date.)]
    (fn [request]
      (if (not-modified-since? request date)
        {:status 304}
        {:status 200
         :content-type "text/javascript"
         :headers {:last-modified (.. (rfc-1123-format) (format date))}
         :body body}))))
