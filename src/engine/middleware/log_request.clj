(ns engine.middleware.log-request)

(defn log [handler]
  (fn [request]
    (println (format "Current request is %s" request))
    (handler request)))
