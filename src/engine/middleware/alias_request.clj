(ns engine.middleware.alias-request
  (:refer-clojure :exclude [alias]))

(defn alias [handler from to]
  (fn [request]
    (let [{:keys [uri]} request]
     (handler (assoc request :uri (if (= from uri) to uri))))))
