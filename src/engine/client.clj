(ns engine.client
  (:use [engine.core :only (defdispatcher)]
        [hiccup.page :only (html5)]
        [cssgen :only (css rule)])
  (:import (java.util Date Locale TimeZone)
           java.text.SimpleDateFormat))

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

(let [handler
      (unchanged-handler
       "text/html; charset=utf-8"
       (html5 {:lang "en"}
              [:head
               [:title "Engine"]
               [:meta {:charset "utf-8"}]
               [:meta {:http-equiv "X-UA-Compatible" :content "chrome=1"}]
               [:link {:rel "shortcut icon" :href "client/gears-32x32.png"}]
               [:link {:rel "stylesheet" :type "text/css" :media "screen" :href "client/main.css"}]
               [:script {:src "client/socket.io.js"}]
               [:script {:data-main "client/main" :src "client/require.js"}]]
              [:body
               [:div {:id "content"} [:pre {:id "editor"}]]
               [:div {:id "meta"}]]))]
  (defdispatcher "/"
    (fn [request]
      (assoc (handler request) :session (:session request)))))


(defn css-display-box []
  (interleave (cycle [:display]) ["box" "-webkit-box" "-moz-box"]))

(defn css-box-orient [orient]
  (interleave [:box-orient :-webkit-box-orient :-moz-box-orient] (cycle [orient])))

(defn css-box-flex [flex]
  (interleave [:box-flex :-webkit-box-flex :-moz-box-flex] (cycle [flex])))

(defmacro flexrule [selector & forms]
  `(apply rule ~selector (concat ~@forms)))

(defdispatcher "/client/main.css"
  (unchanged-handler
   "text/css"
   (css (flexrule "body" [:margin 0 :position "absolute" :top 0 :left 0 :right 0 :bottom 0 :overflow "hidden" :font-size "12px"]
                  (css-display-box) (css-box-orient "vertical"))
        (flexrule "#content" (css-box-flex 1) (css-display-box)) (flexrule "#content > *" (css-box-flex 1))
        (flexrule ".hbox" (css-box-flex 1) (css-display-box) (css-box-orient "horizontal")) (rule ".hbox > *" :width "50%")
        (flexrule ".vbox" (css-box-flex 1) (css-display-box) (css-box-orient "vertical")) (rule ".vbox > *" :height "50%")
        (rule "#meta" :height "20px") (rule "#meta > *" :width "100%" :margin 0)
        (rule "#editor" :position "relative" :margin 0))))
