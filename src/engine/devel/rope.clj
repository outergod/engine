(ns engine.devel.rope
  (:use engine.server.rope
        lacij.graph.core lacij.graph.svg.graph lacij.layouts.layout
        [engine.core :only [defdispatcher]])
  (:require [clojure.zip :as zip])
  (:import java.io.StringWriter
           javax.xml.transform.dom.DOMSource
           javax.xml.transform.stream.StreamResult
           javax.xml.transform.TransformerFactory))

(def *pdf-example* (rope "a" (rope "bc" (rope "d" "ef"))))

(defn svgdoc->string ; this thing is definitely missing in lacij
  [doc & options]
  (let [src (DOMSource. doc)
        writer (StringWriter.)
        result (StreamResult. writer)
        xformer (.newTransformer (TransformerFactory/newInstance))
        options (apply hash-map options)]
    (doseq [[k v] options]
      (.setOutputProperty xformer (name k) v))
    (.transform xformer src result)
    (.toString writer)))

(defn rope->graph [coll]
  (let [digest (fn [obj] (-> obj System/identityHashCode str keyword))]
    (loop [node (rope-zip coll), graph (create-graph)]
      (if (not (zip/end? node))
        (let [rope (zip/node node), id (digest rope),
              graph-node (add-node graph id (print-str rope) 0 0 :shape (if (rope? rope) :rect :circle))]
          (recur (zip/next node)
                 (if-let [parent (zip/up node)]
                   (let [parent-node (zip/node parent), parent-id (digest parent-node)]
                     (add-edge graph-node (format "%s->%s" id parent-id) id parent-id))
                   graph-node)))
        (-> graph (layout :hierarchical) build)))))

(defn rope->svg
  ([coll] (rope->svg coll false))
  ([coll rebalance?]
     (-> (if rebalance? (rope-rebalance coll) coll) rope->graph view svgdoc->string)))

(defdispatcher "/devel/test.svg"
  (fn [request]
    {:status 200
     :content-type "image/svg+xml"
     :body
     (-> (create-graph)
         (add-node :athena "Athena" 10 30)
         (add-node :zeus "Zeus" 200 150)
         (add-node :hera "Hera" 500 150)
         (add-node :ares "Ares" 350 250)
         (add-node :matrimony "♥" 400 170 :shape :circle :style {:fill "salmon"})
         (add-edge :father1 :athena :zeus)
         (add-edge :zeus-matrimony :zeus :matrimony)
         (add-edge :hera-matrimony :hera :matrimony)
         (add-edge :son-zeus-hera :ares :matrimony)
         (layout :hierarchical)
         build view svgdoc->string)}))

(defn svgdispatcher [path svg]
  (defdispatcher path
    (fn [request]
      {:status 200
       :content-type "image/svg+xml"
       :body svg})))

(svgdispatcher "/devel/pdf.svg"
               (rope->svg (rope "a" (rope "bc" (rope "d" "ef"))) false))

(svgdispatcher "/devel/wiki.svg"
               (rope->svg (rope (rope (rope (rope "Hello " "my "))
                                      (rope (rope (rope "na" "me i") (rope "s")) (rope (rope " Simon")))))))
