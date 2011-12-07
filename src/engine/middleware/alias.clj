(ns engine.middleware.alias
  (:use [clojure.string :as string]))

(defn alias-request [handler path alias]
  (fn [request]
    (println "got it")
    (handler (assoc request
               :uri (string/replace (:uri request) (re-pattern (str "^/" path)) (str "/" alias))))))
