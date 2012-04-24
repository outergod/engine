(ns engine.core
  (:use [lamina.core :only (enqueue async)]
        [aleph.http]
        [ring.middleware.stacktrace :only (wrap-stacktrace)]
        [ring.middleware.session :only (wrap-session)]
        [net.cgrand.moustache :only (app)]
        [compojure.core :only (routes)]
        [hiccup.page-helpers :only (html5)]
        [cssgen :only (css rule)])
  (:require [engine server]
            [engine.data.buffer :as buffer]
            [compojure.route :as route]
            [compojure.handler :as handler]
            [engine.middleware.log-request :as log-request]
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

(defn index [request]
  {:status 200
   :headers {"content-type" "text/html; charset=utf-8"}
   :body (html5 {:lang "en"}
                [:head
                 [:title "Engine"]
                 [:meta {:charset "utf-8"}]
                 [:meta {:http-equiv "X-UA-Compatible" :content "chrome=1"}]
                 [:link {:rel "shortcut icon" :href "client/gears.png"}]
                 [:style {:type "text/css" :media "screen"}
                  (css (rule "body" :overflow "none")
                       (rule "#editor" :margin 0 :position "absolute" :top 0 :bottom 0 :left 0 :right 0))]
                 #_[:script {:src "/client/goog/base.js"}]
                 [:script {:data-main "client/main" :src "client/require.js"}]
                 #_[:script {:data-main "client/client" :src "client/require.js"}]
                 #_[:script {:src "client/client.js"}]]
                [:body [:pre {:id "editor"}]])
   :session (:session request)})

(def dispatcher
  (app
   (log-request/log)
   (wrap-stacktrace)
   (wrap-session {:cookie-name "engine"})
   (socket-io/socket.io-handler engine.server/server)
   ["client" "ace" &] (app
                       ["theme" &] (route/resources "/" {:root "support/ace/build/src"})
                       [&] (route/resources "/" {:root "support/ace/lib/ace"}))
   ["client" "pilot" &] (route/resources "/" {:root "support/ace/lib/pilot"})
   ["client" "socket.io" &] (route/resources "/" {:root "support/socket.io-client/dist"})
   ["client" &] (route/resources "/" {:root "client"})
   [""] index
   [&] (routes server-dispatch (route/not-found "Not found"))))

(defonce server
  (start-http-server (wrap-ring-handler (fn [& args]
                                          (apply dispatcher args)))
                     {:port 8080 :join? false :websocket true}))
