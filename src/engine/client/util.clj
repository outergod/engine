(ns engine.client.util
  (:use [engine.core :only (defdispatcher)]
        cssgen)
  (:import (java.util Date Locale TimeZone)
           java.text.SimpleDateFormat))

(defn- css-vendor-prefixed [field]
  (map keyword [field (str "-webkit-" field) (str "-moz-" field)]))

(def css-display-box
  (apply mixin
   (interleave (cycle [:display])
               (css-vendor-prefixed "box"))))

(defn css-box [field val]
  (apply mixin
   (interleave (css-vendor-prefixed (str "box-" field))
               (cycle [val]))))

(defn rfc-1123-format []
  (doto (SimpleDateFormat. "E, dd MMM yyyy HH:mm:ss z" Locale/US)
    (.setTimeZone (TimeZone/getTimeZone "UTC"))))

(defn not-modified-since? [{headers :headers} last-modified]
  (when-let [modified-since (:if-modified-since headers)]
    (not (.. (rfc-1123-format) (parse modified-since) (before last-modified)))))

(defn unchanged-handler [mime body]
  (let [date (Date.)]
    (fn [request]
      (if (not-modified-since? request date)
        {:status 304}
        {:status 200
         :content-type mime
         :headers {:last-modified (.. (rfc-1123-format) (format date))}
         :body body}))))

(defn defcss [uri & rules]
  (defdispatcher uri
    (unchanged-handler "text/css" (apply css rules))))
