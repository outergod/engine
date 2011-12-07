(ns engine.core
  (:use [lamina.core :only (enqueue)]
        [aleph.http]
        [ring.middleware.stacktrace :only (wrap-stacktrace)]
        [net.cgrand.moustache :only (app)]
        [compojure.core :only (routes)]
        [compojure.route :as route]
        [compojure.handler :as handler]
        [hiccup.page-helpers :only (html5)]
        [cssgen :only (css rule)]
        [engine.middleware.log-request :as log-request]))

(defonce *dispatch-table*
  (agent {} :validator #(and (map? %1)
                             (and (every? string? (keys %1))
                                  (every? fn? (vals %1))))
         :error-mode :continue))

(defn server-dispatch [request]
  (when-let [handler (@*dispatch-table* (:uri request))]
    (handler request)))

(defn defdispatcher [uri handler]
  (send *dispatch-table* #(assoc %1 uri handler)))

(defn index [channel request]
  (enqueue channel
           {:status 200
            :headers {"content-type" "text/html; charset=utf-8"}
            :body (html5 {:lang "en"}
                         [:head
                          [:title "Engine"]
                          [:meta {:charset "utf-8"}]
                          [:meta {:http-equiv "X-UA-Compatible" :content "chrome=1"}]
                          [:style {:type "text/css" :media "screen"}
                           (css (rule "body" :overflow "none")
                                (rule "#editor" :margin 0 :position "absolute" :top 0 :bottom 0 :left 0 :right 0))]
                          [:script {:data-main "client/main" :src "client/require.js"}]]
                         [:body [:pre {:id "editor"}]])}))


(defn async-dispatcher [channel request]
  (if (:websocket request)
    (println "Websocket request")
    (index channel request)))

(def dispatcher
  (app
   (log-request/log)
   (wrap-stacktrace)
   ["client" "ace" &] (handler/site (route/resources "/" {:root "support/ace/build/src"}))
   ["client" "pilot" &] (handler/site (route/resources "/" {:root "support/ace/lib/pilot"}))
   ["client" "socket.io" &] (handler/site (route/resources "/" {:root "support/socket.io-client/dist"}))
   ["client" &] (handler/site (route/resources "/" {:root "client"}))
   [""] (wrap-aleph-handler async-dispatcher)
   [&] (routes server-dispatch (route/not-found "Not found"))))

(defonce server
    (start-http-server (wrap-ring-handler (fn [& args]
                                            (apply dispatcher args)))
                       {:port 8080 :join? false :websocket true}))
