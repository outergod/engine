(ns engine.client
  (:use [engine.core :only (defdispatcher)]
        [clojurejs.js :as js]
        [clojure.string :only (split join)])
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

(defmacro defjshandler [name js]
  (let [uri (str "/client/engine/"
                 (join "/" (conj (vec (rest (rest (split (str *ns*) #"\."))))
                                 (str name ".js"))))]
    `(defdispatcher ~uri (unchanged-handler (js/js ~js)))))

(defdispatcher "/client/main.js" ; special case in terms of URI, required to have require.js set its base URL
  (unchanged-handler
   (js/js
    (require ["ace/ace-uncompressed" "socket.io/socket.io"]
             (fn []
               (require ["engine/keyboard" "ace/edit_session" "pilot/event" "engine/commands/default-commands" "ace/theme-twilight"]
                        (fn [keyboard edit event]
                          (let [editor (.edit ace "editor")
                                session (get edit "EditSession")]
                            (set! editor.io (.connect io)
                                  editor.bufferName "foo")
                            (.setTheme editor "ace/theme/twilight")
                            (.setKeyboardHandler editor (keyboard editor))
                            (.setShowGutter editor.renderer false)
                            (.setShowPrintMargin editor.renderer false)
                            (.emit editor.io "load-buffer" "foo"
                                   (fn [contents position]
                                     (.setSession editor (new Session contents))
                                     (.moveCursorTo editor (get position row) (get position column))))))))))))

(defjshandler keyboard
; TODO
  (define))
