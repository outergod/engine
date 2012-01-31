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

(defn rope-graph [graph root]
  (loop [graph graph,
         zipper (rope-zip root), parent nil]
    (if (zip/end? zipper)
      graph
      (let [id (-> zipper zip/node System/identityHashCode str keyword),
            parent (zip/up zipper), name (if parent (-> zipper zip/node pr-str) "root")]
        (recur
         (if parent
           (let [parent-id (-> parent zip/node System/identityHashCode str keyword),
                 parent-name (if (zip/up parent) (-> parent zip/node pr-str) "root")]
             (-> graph
                 (add-node id name)
                 (add-edge (str parent-id "->" id) id parent-id)))
           (-> graph (add-node id name)))
         (zip/next zipper) zipper)))))

(svgdispatcher "/devel/test.svg"
               (-> (create-graph)
                   (add-default-node-attrs :r 45 :shape :circle)
                   (rope-graph (rope (rope (rope (rope "Hello " "my "))
                                           (rope (rope (rope "na" "me i") (rope "s")) (rope (rope " Simon"))))))
                   (layout :hierarchical) build view svgdoc->string))
